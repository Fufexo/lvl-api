package com.fufexo.lvl.accounts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.fufexo.lvl.shared.AssignedIdEntity;
import com.fufexo.lvl.shared.CurrencyCode;

/** Immutable: entries are only ever inserted. Corrections would be new, compensating entries. */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry extends AssignedIdEntity {

	private UUID transferId;

	private UUID accountId;

	private long amountMinor;

	@Enumerated(EnumType.STRING)
	private CurrencyCode currency;

	private long balanceAfterMinor;

	private String description;

	private Instant createdAt;

	protected LedgerEntry() {
	}

	LedgerEntry(UUID id, UUID transferId, Account account, long amountMinor, long balanceAfterMinor,
			String description, Instant createdAt) {
		super(id);
		this.transferId = transferId;
		this.accountId = account.id();
		this.currency = account.currency();
		this.amountMinor = amountMinor;
		this.balanceAfterMinor = balanceAfterMinor;
		this.description = description;
		this.createdAt = createdAt;
	}

}
