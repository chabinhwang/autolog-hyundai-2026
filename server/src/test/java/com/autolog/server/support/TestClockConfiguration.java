package com.autolog.server.support;

import java.time.OffsetDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	/** 테스트 기준 시각: 2026-11-20 12:00 (서울) */
	public static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-11-20T12:00:00+09:00");

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(BASE_TIME.toInstant());
	}
}
