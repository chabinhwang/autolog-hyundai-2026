package com.autolog.server.auth;

import com.autolog.server.auth.AuthDtos.AdminView;
import com.autolog.server.auth.AuthDtos.LoginRequest;
import com.autolog.server.auth.AuthDtos.LoginResponse;
import com.autolog.server.auth.AuthDtos.MeResponse;
import com.autolog.server.auth.AuthDtos.OrganizationDetailView;
import com.autolog.server.auth.AuthDtos.OrganizationView;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.global.security.JwtService;
import com.autolog.server.global.time.Times;
import java.time.Clock;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final AdminUserRepository adminUserRepository;
	private final OrganizationRepository organizationRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final Clock clock;
	/** 없는 아이디도 같은 시간만큼 BCrypt 비교를 해서 응답 시간으로 계정 존재를 알 수 없게 한다. */
	private final String dummyHash;

	public AuthService(AdminUserRepository adminUserRepository, OrganizationRepository organizationRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
		this.adminUserRepository = adminUserRepository;
		this.organizationRepository = organizationRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("autolog-dummy-password");
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		Optional<AdminUser> found = adminUserRepository.findByLoginId(request.loginId());
		String hash = found.map(AdminUser::getPasswordHash).orElse(dummyHash);
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (found.isEmpty() || !matches) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		AdminUser admin = found.get();
		Organization organization = organizationRepository.findById(admin.getOrgId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		admin.recordLogin(Times.now(clock));
		JwtService.IssuedToken token = jwtService.issue(admin.getId(), admin.getOrgId());
		return new LoginResponse(token.value(), "Bearer", jwtService.expiresIn().toSeconds(), AdminView.of(admin),
				new OrganizationView(organization.getId(), organization.getName()));
	}

	@Transactional(readOnly = true)
	public MeResponse me(AdminPrincipal principal) {
		AdminUser admin = adminUserRepository.findById(principal.adminUserId())
				.filter(found -> found.getOrgId().equals(principal.orgId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		Organization organization = organizationRepository.findById(admin.getOrgId())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		return new MeResponse(AdminView.of(admin),
				new OrganizationDetailView(organization.getId(), organization.getName(), organization.getBizRegNo()),
				Times.toSeoul(principal.expiresAt()));
	}
}
