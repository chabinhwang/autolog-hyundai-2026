package com.autolog.server.global.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** 관리자 또는 기기 주체를 담는 인증 객체. 자격 증명(토큰 원문)은 보관하지 않는다. */
public class AutologAuthentication extends AbstractAuthenticationToken {

	public static final String ROLE_ADMIN = "ADMIN";
	public static final String ROLE_DEVICE = "DEVICE";

	private final Object principal;

	private AutologAuthentication(Object principal, String role) {
		super(List.of(new SimpleGrantedAuthority("ROLE_" + role)));
		this.principal = principal;
		setAuthenticated(true);
	}

	public static AutologAuthentication admin(AdminPrincipal principal) {
		return new AutologAuthentication(principal, ROLE_ADMIN);
	}

	public static AutologAuthentication device(DevicePrincipal principal) {
		return new AutologAuthentication(principal, ROLE_DEVICE);
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}
}
