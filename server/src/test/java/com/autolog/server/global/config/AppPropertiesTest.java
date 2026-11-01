package com.autolog.server.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 설정 오류는 서버 시작 실패로 드러나야 한다 (config.md). */
class AppPropertiesTest {

	private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesOnly.class);

	@Test
	void validSettingsBindWithDefaults() {
		runner.withPropertyValues("autolog.jwt.secret=" + SECRET_32,
						"autolog.cors.allowed-origins=https://a.example, http://localhost:5173",
						"autolog.consent-version=2026-11-01")
				.run(context -> {
					assertThat(context).hasNotFailed();
					AppProperties properties = context.getBean(AppProperties.class);
					assertThat(properties.jwt().expiresHours()).isEqualTo(12);
					assertThat(properties.cors().allowedOrigins()).containsExactly("https://a.example",
							"http://localhost:5173");
					assertThat(properties.seed().enabled()).isFalse();
					assertThat(properties.seed().adminDisplayName()).isEqualTo("관리자");
					assertThat(properties.toString()).doesNotContain(SECRET_32);
				});
	}

	@Test
	void shortJwtSecretFailsStartup() {
		runner.withPropertyValues("autolog.jwt.secret=" + SECRET_32.substring(1),
						"autolog.cors.allowed-origins=http://localhost:5173", "autolog.consent-version=2026-11-01")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("32바이트 이상");
				});
	}

	@Test
	void missingJwtSecretFailsStartup() {
		runner.withPropertyValues("autolog.jwt.secret=", "autolog.cors.allowed-origins=http://localhost:5173",
						"autolog.consent-version=2026-11-01")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void missingCorsOriginsFailsStartup() {
		runner.withPropertyValues("autolog.jwt.secret=" + SECRET_32, "autolog.cors.allowed-origins=",
						"autolog.consent-version=2026-11-01")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("CORS_ALLOWED_ORIGINS");
				});
	}

	@Test
	void seedIsEnabledOnlyWhenAllFourValuesExist() {
		assertThat(new AppProperties.Seed("회사", "123-45-67890", "a@b.kr", "pw", null).enabled()).isTrue();
		assertThat(new AppProperties.Seed("회사", "123-45-67890", "a@b.kr", "", null).enabled()).isFalse();
		assertThat(new AppProperties.Seed(null, "123-45-67890", "a@b.kr", "pw", null).enabled()).isFalse();
		assertThat(new AppProperties.Seed("회사", "123-45-67890", "a@b.kr", "secret-pw", null).toString())
				.doesNotContain("secret-pw");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AppProperties.class)
	static class PropertiesOnly {
	}
}
