package com.fufexo.lvl.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param jwtSecret HMAC key for HS256, at least 32 characters (256 bits). Without it the app
 * refuses to start.
 */
@Validated
@ConfigurationProperties("lvl.security")
record SecurityProperties(
		@NotBlank @Size(min = 32, message = "LVL_JWT_SECRET must have at least 32 characters") String jwtSecret,
		@NotBlank String issuer, @NotNull Duration accessTokenTtl, @NotNull Duration refreshTokenTtl) {
}
