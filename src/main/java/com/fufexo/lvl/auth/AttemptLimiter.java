package com.fufexo.lvl.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Fixed-window counters kept in memory: enough for a single instance (the free Render plan runs
 * one). With several instances this would move to a shared store such as Redis.
 */
@Component
class AttemptLimiter {

	private record Window(int count, Instant start) {
	}

	/** Above this many keys, expired windows are purged on the next write. */
	private static final int PURGE_THRESHOLD = 10_000;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private final Clock clock;

	AttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	/** True when {@code key} already used {@code max} attempts in the current window. */
	boolean isExhausted(String key, int max, Duration window) {
		Window current = windows.get(key);
		return current != null && !expired(current, window) && current.count() >= max;
	}

	void record(String key, Duration window) {
		if (windows.size() > PURGE_THRESHOLD) {
			windows.values().removeIf((w) -> expired(w, window));
		}
		windows.compute(key, (k, w) -> (w == null || expired(w, window)) ? new Window(1, clock.instant())
				: new Window(w.count() + 1, w.start()));
	}

	void reset(String key) {
		windows.remove(key);
	}

	private boolean expired(Window window, Duration length) {
		return !window.start().plus(length).isAfter(clock.instant());
	}

}
