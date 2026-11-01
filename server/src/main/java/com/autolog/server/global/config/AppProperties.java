package com.autolog.server.global.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서버 설정 (docs/contracts/config.md). 값은 application.yaml에서 환경변수로 연결한다.
 * 필수값이 없거나 형식이 틀리면 생성자에서 예외를 던져 서버 시작을 실패시킨다.
 * 예외 메시지에 비밀값을 넣지 않는다.
 */
@ConfigurationProperties(prefix = "autolog")
public record AppProperties(Jwt jwt, Cors cors, String consentVersion, Seed seed) {

	public AppProperties {
		if (jwt == null) {
			throw new IllegalArgumentException("JWT_SECRET이 설정되지 않았습니다.");
		}
		if (cors == null) {
			throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS가 설정되지 않았습니다.");
		}
		if (consentVersion == null || consentVersion.isBlank() || consentVersion.length() > 20) {
			throw new IllegalArgumentException("CONSENT_VERSION은 1~20자여야 합니다.");
		}
		if (seed == null) {
			seed = new Seed(null, null, null, null, null);
		}
	}

	public record Jwt(String secret, Integer expiresHours) {

		/** HS256 키 최소 길이 (바이트) */
		public static final int MIN_SECRET_BYTES = 32;

		public Jwt {
			if (secret == null || secret.isBlank()) {
				throw new IllegalArgumentException("JWT_SECRET이 설정되지 않았습니다.");
			}
			if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
				throw new IllegalArgumentException("JWT_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
			}
			if (expiresHours == null) {
				expiresHours = 12;
			}
			if (expiresHours < 1) {
				throw new IllegalArgumentException("JWT_EXPIRES_HOURS는 1 이상이어야 합니다.");
			}
		}

		@Override
		public String toString() {
			return "Jwt[secret=****, expiresHours=" + expiresHours + "]";
		}
	}

	public record Cors(List<String> allowedOrigins) {

		public Cors {
			allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
					.map(String::trim)
					.filter(origin -> !origin.isEmpty())
					.toList();
			if (allowedOrigins.isEmpty()) {
				throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS가 설정되지 않았습니다.");
			}
		}
	}

	/**
	 * 회사·관리자 시드. 네 값(orgName, bizRegNo, adminLoginId, adminPassword)이 모두 있을 때만 실행한다.
	 */
	public record Seed(String orgName, String bizRegNo, String adminLoginId, String adminPassword,
			String adminDisplayName) {

		public Seed {
			orgName = blankToNull(orgName);
			bizRegNo = blankToNull(bizRegNo);
			adminLoginId = blankToNull(adminLoginId);
			// 비밀번호는 앞뒤 공백도 값으로 취급한다.
			adminPassword = adminPassword == null || adminPassword.isEmpty() ? null : adminPassword;
			adminDisplayName = blankToNull(adminDisplayName);
			if (adminDisplayName == null) {
				adminDisplayName = "관리자";
			}
		}

		public boolean enabled() {
			return orgName != null && bizRegNo != null && adminLoginId != null && adminPassword != null;
		}

		@Override
		public String toString() {
			// 비밀번호가 로그·오류 메시지에 섞이지 않도록 한다.
			return "Seed[orgName=" + orgName + ", bizRegNo=" + bizRegNo + ", adminLoginId=" + adminLoginId
					+ ", adminPassword=****, adminDisplayName=" + adminDisplayName + "]";
		}

		private static String blankToNull(String value) {
			return value == null || value.isBlank() ? null : value.trim();
		}
	}
}
