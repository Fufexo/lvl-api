package com.fufexo.lvl.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Instants {

	private Instants() {
	}

	/** Postgres stores microseconds; truncating keeps in-memory and stored instants equal. */
	public static Instant now(Clock clock) {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
