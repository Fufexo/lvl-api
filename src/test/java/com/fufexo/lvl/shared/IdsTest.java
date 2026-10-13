package com.fufexo.lvl.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdsTest {

	@Test
	void generatesVersion7Uuids() {
		UUID id = Ids.next();
		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void embedsTheCreationTimeSoIdsSortByTime() {
		Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
		UUID first = Ids.next(Clock.fixed(earlier, ZoneOffset.UTC));
		UUID second = Ids.next(Clock.fixed(earlier.plusMillis(1), ZoneOffset.UTC));
		assertThat(first.getMostSignificantBits() >>> 16).isEqualTo(earlier.toEpochMilli());
		assertThat(first.toString()).isLessThan(second.toString());
	}

}
