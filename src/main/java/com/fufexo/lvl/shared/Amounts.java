package com.fufexo.lvl.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion between decimal amounts at the API edge and minor units (céntimos / cents) in the
 * domain. Money never travels as a floating point number.
 */
public final class Amounts {

	private static final int SCALE = 2;

	private Amounts() {
	}

	/** Exact conversion; fails if the amount has more than two decimals. */
	public static long toMinor(BigDecimal amount) {
		return amount.setScale(SCALE, RoundingMode.UNNECESSARY).movePointRight(SCALE).longValueExact();
	}

	public static BigDecimal fromMinor(long minor) {
		return BigDecimal.valueOf(minor, SCALE);
	}

}
