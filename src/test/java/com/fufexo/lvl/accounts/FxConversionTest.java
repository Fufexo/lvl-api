package com.fufexo.lvl.accounts;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static com.fufexo.lvl.shared.CurrencyCode.PEN;
import static com.fufexo.lvl.shared.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;

class FxConversionTest {

	private static final BigDecimal RATE = new BigDecimal("3.75");

	@Test
	void sameCurrencyIsUnchanged() {
		assertThat(FxConversion.convert(12_345, PEN, PEN, RATE)).isEqualTo(12_345);
	}

	@Test
	void dollarsToSolesMultiplies() {
		assertThat(FxConversion.convert(10_000, USD, PEN, RATE)).isEqualTo(37_500);
	}

	@Test
	void solesToDollarsDivides() {
		assertThat(FxConversion.convert(37_500, PEN, USD, RATE)).isEqualTo(10_000);
	}

	@Test
	void roundsHalfToEven() {
		// 1 céntimo / 3.75 = 0.266… → 0 cents; 2 céntimos → 0.53… → 1 cent.
		assertThat(FxConversion.convert(1, PEN, USD, RATE)).isZero();
		assertThat(FxConversion.convert(2, PEN, USD, RATE)).isEqualTo(1);
		// 0.5 exactly rounds to the even neighbour.
		assertThat(FxConversion.convert(1, USD, PEN, new BigDecimal("2.5"))).isEqualTo(2);
		assertThat(FxConversion.convert(1, USD, PEN, new BigDecimal("3.5"))).isEqualTo(4);
	}

}
