package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fufexo.lvl.shared.CurrencyCode;

/**
 * Converts minor units between PEN and USD. Both currencies have two decimals, so cents × rate
 * gives céntimos directly. Rounds half-even (banker's rounding) to avoid a systematic bias.
 */
final class FxConversion {

	private FxConversion() {
	}

	static long convert(long amountMinor, CurrencyCode from, CurrencyCode to, BigDecimal usdToPen) {
		if (from == to) {
			return amountMinor;
		}
		BigDecimal amount = BigDecimal.valueOf(amountMinor);
		BigDecimal converted = (from == CurrencyCode.USD) ? amount.multiply(usdToPen)
				: amount.divide(usdToPen, 0, RoundingMode.HALF_EVEN);
		return converted.setScale(0, RoundingMode.HALF_EVEN).longValueExact();
	}

}
