package com.fufexo.lvl.accounts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.shared.AssignedIdEntity;
import com.fufexo.lvl.shared.CurrencyCode;
import com.fufexo.lvl.shared.Ids;

/**
 * A user's wallet in one currency, or a system account (treasury, FX clearing). The balance is a
 * running total of its ledger entries, kept on the row so reads do not sum the whole history.
 */
@Entity
@Table(name = "accounts")
class Account extends AssignedIdEntity {

	enum Kind {

		USER, SYSTEM

	}

	@Nullable
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	private Kind kind;

	@Nullable
	private String code;

	@Enumerated(EnumType.STRING)
	private CurrencyCode currency;

	private long balanceMinor;

	private Instant createdAt;

	protected Account() {
	}

	private Account(UUID id, UUID ownerId, CurrencyCode currency, Instant createdAt) {
		super(id);
		this.ownerId = ownerId;
		this.kind = Kind.USER;
		this.currency = currency;
		this.createdAt = createdAt;
	}

	static Account openFor(UUID ownerId, CurrencyCode currency, Instant now) {
		return new Account(Ids.next(), ownerId, currency, now);
	}

	/** Applies one ledger entry and returns the new balance. */
	long post(long amountMinor) {
		balanceMinor = Math.addExact(balanceMinor, amountMinor);
		return balanceMinor;
	}

	/** Only user accounts must stay at or above zero; system accounts mirror what users hold. */
	boolean isOverdrawn() {
		return kind == Kind.USER && balanceMinor < 0;
	}

	UUID id() {
		return getId();
	}

	@Nullable
	UUID ownerId() {
		return ownerId;
	}

	CurrencyCode currency() {
		return currency;
	}

	long balanceMinor() {
		return balanceMinor;
	}

}
