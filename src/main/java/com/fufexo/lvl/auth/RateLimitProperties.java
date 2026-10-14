package com.fufexo.lvl.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxLoginFailures failed sign-ins per gamertag or email before it is paused
 * @param loginWindow how long those failures are remembered
 * @param maxSignups sign-ups per client IP within {@code signupWindow}
 */
@Validated
@ConfigurationProperties("lvl.rate-limit")
record RateLimitProperties(@Positive int maxLoginFailures, @NotNull Duration loginWindow, @Positive int maxSignups,
		@NotNull Duration signupWindow) {
}
