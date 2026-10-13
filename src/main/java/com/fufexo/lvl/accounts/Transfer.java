package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.shared.AssignedIdEntity;
import com.fufexo.lvl.shared.CurrencyCode;

/** One business operation. Its ledger entries record how money moved; this row records why. */
@Entity
@Table(name = "transfers")
class Transfer extends AssignedIdEntity {

	enum Kind {

		/** Treasury → new user, at sign-up. */
		WELCOME,
		/** Between two accounts of the same user, possibly with currency exchange. */
		INTERNAL,
		/** To another user's account in the same currency. */
		P2P

	}

	@Enumerated(EnumType.STRING)
	private Kind kind;

	private UUID initiatedBy;

	@Nullable
	private String idempotencyKey;

	@Nullable
	private String requestHash;

	private UUID sourceAccountId;

	private UUID targetAccountId;

	private long sourceAmountMinor;

	@Enumerated(EnumType.STRING)
	private CurrencyCode sourceCurrency;

	private long targetAmountMinor;

	@Enumerated(EnumType.STRING)
	private CurrencyCode targetCurrency;

	@Nullable
	@Column(precision = 18, scale = 8)
	private BigDecimal fxRate;

	@Nullable
	private String note;

	private Instant createdAt;

	protected Transfer() {
	}

	Transfer(UUID id, Kind kind, UUID initiatedBy, @Nullable String idempotencyKey, @Nullable String requestHash,
			Account source, long sourceAmountMinor, Account target, long targetAmountMinor,
			@Nullable BigDecimal fxRate, @Nullable String note, Instant createdAt) {
		super(id);
		this.kind = kind;
		this.initiatedBy = initiatedBy;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.sourceAccountId = source.id();
		this.sourceCurrency = source.currency();
		this.sourceAmountMinor = sourceAmountMinor;
		this.targetAccountId = target.id();
		this.targetCurrency = target.currency();
		this.targetAmountMinor = targetAmountMinor;
		this.fxRate = fxRate;
		this.note = note;
		this.createdAt = createdAt;
	}

	UUID id() {
		return getId();
	}

	Kind kind() {
		return kind;
	}

	@Nullable
	String requestHash() {
		return requestHash;
	}

	UUID sourceAccountId() {
		return sourceAccountId;
	}

	UUID targetAccountId() {
		return targetAccountId;
	}

	long sourceAmountMinor() {
		return sourceAmountMinor;
	}

	CurrencyCode sourceCurrency() {
		return sourceCurrency;
	}

	long targetAmountMinor() {
		return targetAmountMinor;
	}

	CurrencyCode targetCurrency() {
		return targetCurrency;
	}

	@Nullable
	BigDecimal fxRate() {
		return fxRate;
	}

	@Nullable
	String note() {
		return note;
	}

	Instant createdAt() {
		return createdAt;
	}

}
