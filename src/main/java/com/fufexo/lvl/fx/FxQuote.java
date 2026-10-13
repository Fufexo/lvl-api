package com.fufexo.lvl.fx;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Soles per US dollar at a point in time.
 *
 * @param usdToPen how many PEN one USD buys
 * @param asOf when the provider published the rate
 * @param live false when the provider was unreachable and a fallback rate is in use
 */
public record FxQuote(BigDecimal usdToPen, Instant asOf, boolean live) {
}
