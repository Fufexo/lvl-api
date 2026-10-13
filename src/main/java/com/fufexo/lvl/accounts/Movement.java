package com.fufexo.lvl.accounts;

import java.time.Instant;
import java.util.UUID;

import com.fufexo.lvl.shared.CurrencyCode;

/** Read model of one ledger entry with the kind of its transfer, for the movements list. */
record Movement(UUID id, UUID transferId, Transfer.Kind kind, String description, long amountMinor,
		CurrencyCode currency, long balanceAfterMinor, Instant createdAt) {
}
