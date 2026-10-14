package com.fufexo.lvl.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fufexo.lvl.shared.BusinessException;
import com.fufexo.lvl.shared.Ids;
import com.fufexo.lvl.shared.Instants;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	record Profile(UUID id, String gamertag, String email) {

		static Profile of(User user) {
			return new Profile(user.id(), user.gamertag(), user.email());
		}

	}

	record Session(TokenIssuer.Tokens tokens, Profile user) {
	}

	private static final String INVALID_CREDENTIALS = "Usuario o contraseña incorrectos.";

	private static final String SESSION_EXPIRED = "Tu sesión expiró. Vuelve a ingresar.";

	/** BCrypt only reads the first 72 bytes; longer passwords are rejected instead of truncated. */
	private static final int MAX_PASSWORD_BYTES = 72;

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository users;

	private final RefreshTokenRepository refreshTokens;

	private final TokenIssuer tokenIssuer;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final AttemptLimiter limiter;

	private final RateLimitProperties limits;

	/** Compared against when the user does not exist, so both paths take the same time. */
	private final String dummyHash;

	AuthService(UserRepository users, RefreshTokenRepository refreshTokens, TokenIssuer tokenIssuer,
			PasswordEncoder passwordEncoder, ApplicationEventPublisher events, Clock clock, AttemptLimiter limiter,
			RateLimitProperties limits) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.tokenIssuer = tokenIssuer;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.clock = clock;
		this.limiter = limiter;
		this.limits = limits;
		this.dummyHash = passwordEncoder.encode("timing-equalizer-password");
	}

	/** {@code clientIp} limits how many accounts one client can open per window. */
	@Transactional
	Session register(String gamertag, String email, String password, String clientIp) {
		String signupKey = "signup:" + clientIp;
		if (limiter.isExhausted(signupKey, limits.maxSignups(), limits.signupWindow())) {
			throw tooManyAttempts("Creaste varias cuentas seguidas. Intenta de nuevo más tarde.");
		}
		limiter.record(signupKey, limits.signupWindow());
		if (tooLong(password)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
					"La contraseña es demasiado larga. Usa una más corta.", "password");
		}
		String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
		String trimmedGamertag = gamertag.strip();
		if (users.findByGamertag(trimmedGamertag).isPresent()) {
			throw BusinessException.conflict("GAMERTAG_TAKEN", "Ese gamertag ya está en uso.", "gamertag");
		}
		if (users.findByEmail(normalizedEmail).isPresent()) {
			throw BusinessException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo.", "email");
		}
		User user = new User(Ids.next(), trimmedGamertag, normalizedEmail, passwordEncoder.encode(password),
				Instants.now(clock));
		try {
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race against a simultaneous sign-up with the same gamertag or email.
			throw BusinessException.conflict("ACCOUNT_EXISTS", "Ese gamertag o correo ya está en uso.", null);
		}
		events.publishEvent(new UserRegistered(user.id(), user.gamertag()));
		return new Session(tokenIssuer.issue(user), Profile.of(user));
	}

	/**
	 * {@code login} is a gamertag or an email. After too many failures for the same identifier,
	 * sign-in is paused for a while, so passwords cannot be guessed by brute force.
	 */
	@Transactional
	Session login(String login, String password) {
		String identifier = login.strip();
		String failuresKey = "login:" + identifier.toLowerCase(Locale.ROOT);
		if (limiter.isExhausted(failuresKey, limits.maxLoginFailures(), limits.loginWindow())) {
			throw tooManyAttempts("Demasiados intentos fallidos. Espera unos minutos e intenta de nuevo.");
		}
		var user = identifier.contains("@") ? users.findByEmail(identifier) : users.findByGamertag(identifier);
		String hash = user.map(User::passwordHash).orElse(dummyHash);
		boolean matches = !tooLong(password) && passwordEncoder.matches(password, hash);
		if (user.isEmpty() || !matches) {
			limiter.record(failuresKey, limits.loginWindow());
			throw BusinessException.unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
		}
		limiter.reset(failuresKey);
		return new Session(tokenIssuer.issue(user.get()), Profile.of(user.get()));
	}

	/**
	 * Rotates the refresh token. A token that was already used signals theft or replay: the whole
	 * family is revoked and every device of that sign-in has to log in again.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	Session refresh(String refreshToken) {
		Instant now = Instants.now(clock);
		RefreshToken current = refreshTokens.findByTokenHashForUpdate(TokenIssuer.hash(refreshToken))
			.orElseThrow(() -> BusinessException.unauthorized("SESSION_EXPIRED", SESSION_EXPIRED));
		if (current.isSpent()) {
			log.warn("Refresh token reuse detected: revoking session family {} of user {}", current.familyId(),
					current.userId());
			refreshTokens.revokeFamily(current.familyId(), now);
			throw BusinessException.unauthorized("SESSION_REVOKED", SESSION_EXPIRED);
		}
		if (current.isExpired(now)) {
			throw BusinessException.unauthorized("SESSION_EXPIRED", SESSION_EXPIRED);
		}
		current.markUsed(now);
		User user = users.findById(current.userId())
			.orElseThrow(() -> BusinessException.unauthorized("SESSION_EXPIRED", SESSION_EXPIRED));
		return new Session(tokenIssuer.issue(user, current.familyId()), Profile.of(user));
	}

	@Transactional
	void logout(String refreshToken) {
		refreshTokens.findByTokenHashForUpdate(TokenIssuer.hash(refreshToken))
			.ifPresent((token) -> refreshTokens.revokeFamily(token.familyId(), Instants.now(clock)));
	}

	@Transactional(readOnly = true)
	Profile profile(UUID userId) {
		return users.findById(userId)
			.map(Profile::of)
			.orElseThrow(() -> BusinessException.unauthorized("SESSION_EXPIRED", SESSION_EXPIRED));
	}

	private static boolean tooLong(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
	}

	private static BusinessException tooManyAttempts(String message) {
		return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", message, null);
	}

}
