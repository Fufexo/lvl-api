package com.fufexo.lvl.fx;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("lvl.fx")
record FxProperties(@NotNull URI url, @NotNull Duration ttl, @NotNull @Positive BigDecimal fallbackUsdPen) {
}
