package com.autolog.server.global.security;

import com.autolog.server.device.Device;
import com.autolog.server.device.DeviceRepository;
import com.autolog.server.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 값을 읽어 호출 주체를 정한다 (rules §1 토큰 구분).
 * <ul>
 * <li>점 2개(JWT 형식) → 관리자 JWT 검증</li>
 * <li>그 외 → 기기 토큰. SHA-256 hex로 device.token_hash를 찾고, 없거나 폐기됐으면 DEVICE_TOKEN_INVALID</li>
 * </ul>
 * 검증에 실패해도 여기서 바로 응답하지 않고 실패 사유만 요청 속성에 남긴다.
 * 인증이 필요한 경로라면 {@link JsonAuthenticationEntryPoint}가 그 사유로 401을 만든다 (공개 경로는 영향 없음).
 * 토큰 원문은 로그에 남기지 않는다.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	/** 인증 실패 사유(ErrorCode)를 담는 요청 속성 이름 */
	public static final String AUTH_ERROR_ATTRIBUTE = BearerTokenAuthenticationFilter.class.getName() + ".error";

	private static final String BEARER_PREFIX = "bearer ";

	private final JwtService jwtService;
	private final DeviceRepository deviceRepository;

	public BearerTokenAuthenticationFilter(JwtService jwtService, DeviceRepository deviceRepository) {
		this.jwtService = jwtService;
		this.deviceRepository = deviceRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null) {
			authenticate(header, request);
		}
		chain.doFilter(request, response);
	}

	private void authenticate(String header, HttpServletRequest request) {
		if (header.length() <= BEARER_PREFIX.length()
				|| !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
			return;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
			return;
		}
		if (isJwtShape(token)) {
			Optional<AdminPrincipal> admin = jwtService.verify(token);
			if (admin.isEmpty()) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
				return;
			}
			setAuthentication(AutologAuthentication.admin(admin.get()));
			return;
		}
		Optional<Device> device = deviceRepository.findByTokenHash(TokenHasher.sha256Hex(token))
				.filter(found -> !found.isRevoked());
		if (device.isEmpty()) {
			request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.DEVICE_TOKEN_INVALID);
			return;
		}
		Device found = device.get();
		setAuthentication(AutologAuthentication.device(
				new DevicePrincipal(found.getId(), found.getOrgId(), found.getVehicleId())));
	}

	/** JWT 형식 = 점이 정확히 2개 */
	static boolean isJwtShape(String token) {
		int dots = 0;
		for (int i = 0; i < token.length(); i++) {
			if (token.charAt(i) == '.') {
				dots++;
			}
		}
		return dots == 2;
	}

	private static void setAuthentication(AutologAuthentication authentication) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
	}
}
