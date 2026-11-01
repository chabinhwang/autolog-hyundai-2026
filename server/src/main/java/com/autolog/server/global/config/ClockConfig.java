package com.autolog.server.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	/** 서버 시각. 테스트에서는 고정·이동 가능한 Clock으로 교체한다. */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
