package com.autolog.server.global.security;

/** 기기 토큰으로 인증된 호출 주체 (차량 1대) */
public record DevicePrincipal(Long deviceId, Long orgId, Long vehicleId) {
}
