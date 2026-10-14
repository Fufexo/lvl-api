package com.fufexo.lvl.fx;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the rate from open.er-api.com (free, no key) and keeps it in memory for {@code lvl.fx.ttl}.
 * The provider updates once a day, so one request per hour is plenty and keeps the API fast and
 * within the provider's fair use. If it fails, the last good rate is served; before the first
 * success, a configured fallback.
 */
@Service
class OpenErApiExchangeRates implements ExchangeRates {

	private static final Logger log = LoggerFactory.getLogger(OpenErApiExchangeRates.class);

	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	record ProviderResponse(String result, Map<String, BigDecimal> rates,
			@JsonProperty("time_last_update_unix") long updatedAtEpochSecond) {
	}

	private record Cached(FxQuote quote, Instant fetchedAt) {
	}

	private final RestClient http;

	private final FxProperties properties;

	private final Clock clock;

	// A lock rather than `synchronized`, so waiting virtual threads do not pin their carrier.
	private final ReentrantLock lock = new ReentrantLock();

	@Nullable
	private Cached cached;

	OpenErApiExchangeRates(FxProperties properties, Clock clock) {
		var requestFactory = new JdkClientHttpRequestFactory(
				java.net.http.HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
		requestFactory.setReadTimeout(TIMEOUT);
		this.http = RestClient.builder().requestFactory(requestFactory).build();
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public FxQuote usdToPen() {
		lock.lock();
		try {
			Instant now = clock.instant();
			if (cached != null && cached.fetchedAt().plus(properties.ttl()).isAfter(now)) {
				return cached.quote();
			}
			try {
				cached = new Cached(fetch(), now);
				return cached.quote();
			}
			catch (RestClientException | IllegalStateException ex) {
				log.warn("Exchange rate provider unavailable: {}", ex.getMessage());
				if (cached != null) {
					return new FxQuote(cached.quote().usdToPen(), cached.quote().asOf(), false);
				}
				return new FxQuote(properties.fallbackUsdPen(), now, false, true);
			}
		}
		finally {
			lock.unlock();
		}
	}

	private FxQuote fetch() {
		ProviderResponse response = http.get().uri(properties.url()).retrieve().body(ProviderResponse.class);
		if (response == null || !"success".equals(response.result()) || response.rates() == null
				|| response.rates().get("PEN") == null) {
			throw new IllegalStateException("Unexpected provider response");
		}
		return new FxQuote(response.rates().get("PEN"), Instant.ofEpochSecond(response.updatedAtEpochSecond()),
				true);
	}

}
