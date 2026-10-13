package com.fufexo.lvl.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.fufexo.lvl.shared.Hashing;
import com.fufexo.lvl.shared.Ids;
import com.fufexo.lvl.shared.Instants;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Access tokens are short-lived JWTs (stateless, checked by signature). Refresh tokens are opaque
 * random strings stored as SHA-256 hashes, so a database leak does not expose usable tokens.
 */
@Component
class TokenIssuer {

	record Tokens(String accessToken, long expiresInSeconds, String refreshToken) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final JwtEncoder jwtEncoder;

	private final RefreshTokenRepository refreshTokens;

	private final SecurityProperties properties;

	private final Clock clock;

	TokenIssuer(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, SecurityProperties properties,
			Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.refreshTokens = refreshTokens;
		this.properties = properties;
		this.clock = clock;
	}

	/** Starts a new refresh token family (a new sign-in). */
	Tokens issue(User user) {
		return issue(user, Ids.next());
	}

	/** Continues an existing family (a refresh). */
	Tokens issue(User user, UUID familyId) {
		Instant now = Instants.now(clock);
		String refreshToken = randomToken();
		refreshTokens.save(new RefreshToken(Ids.next(), user.id(), familyId, hash(refreshToken),
				now.plus(properties.refreshTokenTtl()), now));
		return new Tokens(accessToken(user, now), properties.accessTokenTtl().toSeconds(), refreshToken);
	}

	private String accessToken(User user, Instant now) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(user.id().toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl()))
			.claim("gamertag", user.gamertag())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	static String hash(String token) {
		return Hashing.sha256Hex(token);
	}

	private static String randomToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
