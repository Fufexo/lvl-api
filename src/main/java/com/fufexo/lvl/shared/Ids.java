package com.fufexo.lvl.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): a 48-bit Unix timestamp in milliseconds followed by random bits.
 * Ids sort roughly by creation time, which keeps B-tree indexes compact, and they are not
 * guessable like sequential numbers.
 */
public final class Ids {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Ids() {
	}

	public static UUID next() {
		return next(Clock.systemUTC());
	}

	static UUID next(Clock clock) {
		long millis = clock.millis();
		long msb = (millis << 16) | 0x7000L | RANDOM.nextInt(1 << 12);
		long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
		return new UUID(msb, lsb);
	}

}
