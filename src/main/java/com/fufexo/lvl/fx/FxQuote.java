package com.fufexo.lvl.fx;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Soles per US dollar at a point in time.
 *
 * @param usdToPen how many PEN one USD buys
 * @param asOf when the provider published the rate
 * @param live false when the provider is unreachable and an older rate is being served
 * @param fallback true when no real rate was ever obtained and the configured default is in use
 */
public record FxQuote(BigDecimal usdToPen, Instant asOf, boolean live, boolean fallback) {

	public FxQuote(BigDecimal usdToPen, Instant asOf, boolean live) {
		this(usdToPen, asOf, live, false);
	}

}
