package com.fufexo.lvl.accounts;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.fufexo.lvl.shared.CurrencyCode;
import com.fufexo.lvl.shared.Ids;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts balanced sets of entries. The only code that changes a balance.
 *
 * <p>
 * Accounts are locked ({@code SELECT … FOR UPDATE}) in a fixed order before any balance is read,
 * so concurrent transfers touching the same accounts queue up instead of overwriting each other,
 * and cannot deadlock.
 */
@Component
class Ledger {

	/** One side of a posting: a signed amount for one account. */
	record Leg(UUID accountId, long amountMinor, String description) {
	}

	static final class InsufficientFunds extends RuntimeException {

		InsufficientFunds() {
			super("Insufficient funds", null, false, false);
		}

	}

	private final EntityManager entityManager;

	private final LedgerEntryRepository entries;

	Ledger(EntityManager entityManager, LedgerEntryRepository entries) {
		this.entityManager = entityManager;
		this.entries = entries;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	void post(UUID transferId, List<Leg> legs, Instant now) {
		Map<UUID, Account> accounts = lockInOrder(legs);
		assertBalanced(legs, accounts);
		for (Leg leg : legs) {
			Account account = accounts.get(leg.accountId());
			long balanceAfter = account.post(leg.amountMinor());
			if (account.isOverdrawn()) {
				throw new InsufficientFunds();
			}
			entries.save(new LedgerEntry(Ids.next(), transferId, account, leg.amountMinor(), balanceAfter,
					leg.description(), now));
		}
	}

	/** Per currency, what leaves some accounts must arrive at others. */
	static void assertBalanced(List<Leg> legs, Map<UUID, Account> accounts) {
		Map<CurrencyCode, Long> totals = new EnumMap<>(CurrencyCode.class);
		for (Leg leg : legs) {
			if (leg.amountMinor() == 0) {
				throw new IllegalArgumentException("Ledger legs cannot be zero");
			}
			totals.merge(accounts.get(leg.accountId()).currency(), leg.amountMinor(), Math::addExact);
		}
		totals.forEach((currency, total) -> {
			if (total != 0) {
				throw new IllegalArgumentException("Unbalanced posting in " + currency + ": " + total);
			}
		});
	}

	private Map<UUID, Account> lockInOrder(List<Leg> legs) {
		Map<UUID, Account> locked = new HashMap<>();
		legs.stream().map(Leg::accountId).distinct().sorted(Comparator.naturalOrder()).forEach((id) -> {
			Account account = entityManager.find(Account.class, id);
			if (account == null) {
				throw new IllegalArgumentException("Unknown account " + id);
			}
			// refresh, not find(…, lock): an account already loaded in this transaction must be
			// re-read under the lock, or its balance could be stale.
			entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
			locked.put(id, account);
		});
		return locked;
	}

}
