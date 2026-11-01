package com.autolog.server.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 인증 API 요청·응답 (docs/contracts/api/auth.md) */
public final class AuthDtos {

	private AuthDtos() {
	}

	public record LoginRequest(
			@NotBlank(message = "비어 있을 수 없습니다.")
			@Email(message = "이메일 형식이어야 합니다.")
			@Size(max = 100, message = "100자 이하여야 합니다.")
			String loginId,
			@NotBlank(message = "비어 있을 수 없습니다.")
			@Size(min = 1, max = 100, message = "1~100자여야 합니다.")
			String password) {

		@Override
		public String toString() {
			return "LoginRequest[loginId=" + loginId + ", password=****]";
		}
	}

	public record LoginResponse(String accessToken, String tokenType, long expiresIn, AdminView admin,
			OrganizationView organization) {

		@Override
		public String toString() {
			return "LoginResponse[accessToken=****]";
		}
	}

	public record MeResponse(AdminView admin, OrganizationDetailView organization, OffsetDateTime expiresAt) {
	}

	public record AdminView(Long id, String loginId, String displayName) {

		static AdminView of(AdminUser admin) {
			return new AdminView(admin.getId(), admin.getLoginId(), admin.getDisplayName());
		}
	}

	public record OrganizationView(Long id, String name) {
	}

	public record OrganizationDetailView(Long id, String name, String bizRegNo) {
	}
}
