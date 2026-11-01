package com.autolog.server.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 DB: 운영과 같은 PostgreSQL 18 (jsonb 때문에 H2 불가). 컨텍스트 캐시로 테스트 전체가 컨테이너 1개를 같이 쓴다.
 * 환경변수 AUTOLOG_TEST_DB_URL이 있으면 컨테이너를 띄우지 않고 그 DB를 쓴다 (Docker 없는 로컬, application-test.yaml).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	@ConditionalOnExpression("'${AUTOLOG_TEST_DB_URL:}'.isEmpty()")
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}
}
