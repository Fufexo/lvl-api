package com.fufexo.lvl.auth;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.shared.BusinessException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The access token travels in the response body (the SPA keeps it in memory); the refresh token
 * only in an HttpOnly cookie that JavaScript cannot read.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Autenticación")
class AuthController {

	static final String REFRESH_COOKIE = "lvl_refresh";

	record RegisterRequest(
			@NotBlank(message = "Ingresa un gamertag.") @Pattern(regexp = "^[A-Za-z0-9_]{3,16}$",
					message = "Usa de 3 a 16 letras, números o guion bajo.") String gamertag,
			@NotBlank(message = "Ingresa tu correo.") @Email(message = "Ingresa un correo válido.") @Size(max = 254,
					message = "El correo es demasiado largo.") String email,
			@NotBlank(message = "Ingresa una contraseña.") @Size(min = 10, max = 72,
					message = "Usa entre 10 y 72 caracteres.") String password) {
	}

	record LoginRequest(@NotBlank(message = "Ingresa tu gamertag o correo.") String login,
			@NotBlank(message = "Ingresa tu contraseña.") String password) {
	}

	record AuthResponse(String accessToken, String tokenType, long expiresIn, AuthService.Profile user) {

		static AuthResponse of(AuthService.Session session) {
			return new AuthResponse(session.tokens().accessToken(), "Bearer", session.tokens().expiresInSeconds(),
					session.user());
		}

	}

	private final AuthService auth;

	private final SecurityProperties properties;

	AuthController(AuthService auth, SecurityProperties properties) {
		this.auth = auth;
		this.properties = properties;
	}

	@PostMapping("/auth/register")
	@Operation(summary = "Crear cuenta", description = "Abre las cuentas PEN y USD con el bono de bienvenida.")
	ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		var session = auth.register(request.gamertag(), request.email(), request.password());
		return withRefreshCookie(HttpStatus.CREATED, session);
	}

	@PostMapping("/auth/login")
	@Operation(summary = "Ingresar con gamertag o correo")
	ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return withRefreshCookie(HttpStatus.OK, auth.login(request.login(), request.password()));
	}

	@PostMapping("/auth/refresh")
	@Operation(summary = "Renovar el access token", description = "Usa y rota la cookie lvl_refresh.")
	ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String token) {
		if (token == null) {
			throw BusinessException.unauthorized("SESSION_EXPIRED", "Tu sesión expiró. Vuelve a ingresar.");
		}
		return withRefreshCookie(HttpStatus.OK, auth.refresh(token));
	}

	@PostMapping("/auth/logout")
	@Operation(summary = "Cerrar sesión", description = "Revoca la sesión en este y otros dispositivos de ese ingreso.")
	ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String token) {
		if (token != null) {
			auth.logout(token);
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString()).build();
	}

	@GetMapping("/me")
	@Operation(summary = "Perfil del usuario actual")
	@SecurityRequirement(name = "bearer")
	AuthService.Profile me(@AuthenticationPrincipal Jwt jwt) {
		return auth.profile(UUID.fromString(jwt.getSubject()));
	}

	private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthService.Session session) {
		ResponseCookie cookie = refreshCookie(session.tokens().refreshToken(),
				properties.refreshTokenTtl().toSeconds());
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(AuthResponse.of(session));
	}

	private static ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(maxAgeSeconds)
			.build();
	}

}
