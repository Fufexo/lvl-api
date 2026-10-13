package com.fufexo.lvl.shared;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

	/** Injected wherever "now" matters, so tests can pin time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
