package com.autolog.server.global.security;

import java.time.Instant;

/** 관리자 JWT로 인증된 호출 주체 */
public record AdminPrincipal(Long adminUserId, Long orgId, Instant expiresAt) {
}
