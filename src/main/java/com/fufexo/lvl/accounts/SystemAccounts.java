package com.fufexo.lvl.accounts;

import java.util.UUID;

import com.fufexo.lvl.shared.CurrencyCode;

/** Fixed ids of the system accounts created by the V2 migration. */
final class SystemAccounts {

	static final UUID TREASURY_PEN = UUID.fromString("00000000-0000-7000-8000-000000000001");

	static final UUID TREASURY_USD = UUID.fromString("00000000-0000-7000-8000-000000000002");

	static final UUID FX_PEN = UUID.fromString("00000000-0000-7000-8000-000000000003");

	static final UUID FX_USD = UUID.fromString("00000000-0000-7000-8000-000000000004");

	private SystemAccounts() {
	}

	static UUID treasury(CurrencyCode currency) {
		return switch (currency) {
			case PEN -> TREASURY_PEN;
			case USD -> TREASURY_USD;
		};
	}

	static UUID fxClearing(CurrencyCode currency) {
		return switch (currency) {
			case PEN -> FX_PEN;
			case USD -> FX_USD;
		};
	}

}
