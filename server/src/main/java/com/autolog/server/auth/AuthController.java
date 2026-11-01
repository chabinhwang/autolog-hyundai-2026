package com.autolog.server.auth;

import com.autolog.server.auth.AuthDtos.LoginRequest;
import com.autolog.server.auth.AuthDtos.LoginResponse;
import com.autolog.server.auth.AuthDtos.MeResponse;
import com.autolog.server.global.response.ApiResponse;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.global.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final RateLimiter rateLimiter;

	public AuthController(AuthService authService, RateLimiter rateLimiter) {
		this.authService = authService;
		this.rateLimiter = rateLimiter;
	}

	/** 관리자 로그인. IP당 분당 10회 */
	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		rateLimiter.check(RateLimiter.Bucket.LOGIN, http.getRemoteAddr());
		return ApiResponse.ok(authService.login(request));
	}

	@GetMapping("/me")
	public ApiResponse<MeResponse> me(@AuthenticationPrincipal AdminPrincipal admin) {
		return ApiResponse.ok(authService.me(admin));
	}
}
