package com.fufexo.lvl.accounts;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Demo money credited at sign-up, so a new user can try transfers right away. */
@Validated
@ConfigurationProperties("lvl.welcome-bonus")
record WelcomeBonusProperties(@NotNull @PositiveOrZero BigDecimal pen, @NotNull @PositiveOrZero BigDecimal usd) {
}
