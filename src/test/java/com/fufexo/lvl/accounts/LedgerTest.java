package com.fufexo.lvl.accounts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fufexo.lvl.shared.CurrencyCode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTest {

	private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

	private final Account penA = Account.openFor(UUID.randomUUID(), CurrencyCode.PEN, now);

	private final Account penB = Account.openFor(UUID.randomUUID(), CurrencyCode.PEN, now);

	private final Account usd = Account.openFor(UUID.randomUUID(), CurrencyCode.USD, now);

	private final Map<UUID, Account> accounts = Map.of(penA.id(), penA, penB.id(), penB, usd.id(), usd);

	@Test
	void acceptsPostingsThatBalancePerCurrency() {
		var legs = List.of(new Ledger.Leg(penA.id(), -500, "a"), new Ledger.Leg(penB.id(), 500, "b"));
		assertThatCode(() -> Ledger.assertBalanced(legs, accounts)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMoneyCreatedFromNothing() {
		var legs = List.of(new Ledger.Leg(penA.id(), -500, "a"), new Ledger.Leg(penB.id(), 600, "b"));
		assertThatThrownBy(() -> Ledger.assertBalanced(legs, accounts)).hasMessageContaining("Unbalanced");
	}

	@Test
	void rejectsBalancingAcrossCurrencies() {
		// -500 PEN and +500 USD add up to zero numerically but are different money.
		var legs = List.of(new Ledger.Leg(penA.id(), -500, "a"), new Ledger.Leg(usd.id(), 500, "b"));
		assertThatThrownBy(() -> Ledger.assertBalanced(legs, accounts)).hasMessageContaining("Unbalanced");
	}

	@Test
	void rejectsZeroLegs() {
		var legs = List.of(new Ledger.Leg(penA.id(), 0, "a"));
		assertThatThrownBy(() -> Ledger.assertBalanced(legs, accounts)).isInstanceOf(IllegalArgumentException.class);
	}

}
