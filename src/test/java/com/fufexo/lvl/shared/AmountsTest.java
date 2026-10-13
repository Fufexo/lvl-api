package com.fufexo.lvl.shared;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountsTest {

	@Test
	void convertsDecimalsToMinorUnitsExactly() {
		assertThat(Amounts.toMinor(new BigDecimal("150"))).isEqualTo(15_000);
		assertThat(Amounts.toMinor(new BigDecimal("0.1"))).isEqualTo(10);
		assertThat(Amounts.toMinor(new BigDecimal("19.99"))).isEqualTo(1_999);
	}

	@Test
	void rejectsFractionsOfACent() {
		assertThatThrownBy(() -> Amounts.toMinor(new BigDecimal("1.005"))).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void convertsBackWithTwoDecimals() {
		assertThat(Amounts.fromMinor(1_999)).isEqualByComparingTo("19.99").hasToString("19.99");
		assertThat(Amounts.fromMinor(-50)).hasToString("-0.50");
	}

}
