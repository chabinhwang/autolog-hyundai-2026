package com.autolog.server.auth;

import com.autolog.server.global.config.AppProperties;
import com.autolog.server.global.time.Times;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회사·관리자 시드 (config.md). SEED_* 네 값이 모두 있을 때만 실행하고,
 * 같은 사업자등록번호의 회사가 이미 있으면 아무것도 바꾸지 않는다 (비밀번호 덮어쓰기 없음).
 * 시드 값이 형식에 맞지 않으면 서버 시작을 실패시킨다. 비밀번호는 로그에 남기지 않는다.
 */
@Component
public class SeedInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedInitializer.class);
	private static final Pattern BIZ_REG_NO = Pattern.compile("\\d{3}-\\d{2}-\\d{5}");
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private final AppProperties properties;
	private final OrganizationRepository organizationRepository;
	private final AdminUserRepository adminUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;

	public SeedInitializer(AppProperties properties, OrganizationRepository organizationRepository,
			AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.properties = properties;
		this.organizationRepository = organizationRepository;
		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public void run(ApplicationArguments args) {
		seed(properties.seed());
	}

	/** 회사·관리자를 한 트랜잭션으로 만든다. @return 새로 만들었으면 true */
	public boolean seed(AppProperties.Seed seed) {
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> seedInTransaction(seed)));
	}

	private boolean seedInTransaction(AppProperties.Seed seed) {
		if (!seed.enabled()) {
			log.info("SEED_* 값이 모두 있지 않아 회사·관리자 시드를 건너뜁니다.");
			return false;
		}
		validate(seed);
		if (organizationRepository.findByBizRegNo(seed.bizRegNo()).isPresent()) {
			log.info("시드 회사가 이미 있어 아무것도 바꾸지 않습니다.");
			return false;
		}
		if (adminUserRepository.existsByLoginId(seed.adminLoginId())) {
			throw new IllegalStateException("SEED_ADMIN_LOGIN_ID가 다른 회사 관리자 아이디와 겹칩니다.");
		}
		Instant now = Times.now(clock);
		Organization organization = organizationRepository.save(new Organization(seed.orgName(), seed.bizRegNo(), now));
		adminUserRepository.save(new AdminUser(organization.getId(), seed.adminLoginId(),
				passwordEncoder.encode(seed.adminPassword()), seed.adminDisplayName(), now));
		log.info("시드 회사·관리자를 만들었습니다 (orgId={}).", organization.getId());
		return true;
	}

	private static void validate(AppProperties.Seed seed) {
		if (seed.orgName().length() > 100) {
			throw new IllegalStateException("SEED_ORG_NAME은 100자 이하여야 합니다.");
		}
		if (!BIZ_REG_NO.matcher(seed.bizRegNo()).matches()) {
			throw new IllegalStateException("SEED_BIZ_REG_NO는 000-00-00000 형식이어야 합니다.");
		}
		if (seed.adminLoginId().length() > 100 || !EMAIL.matcher(seed.adminLoginId()).matches()) {
			throw new IllegalStateException("SEED_ADMIN_LOGIN_ID는 100자 이하 이메일 형식이어야 합니다.");
		}
		if (seed.adminPassword().length() > 100) {
			throw new IllegalStateException("SEED_ADMIN_PASSWORD는 100자 이하여야 합니다.");
		}
		if (seed.adminDisplayName().length() > 30) {
			throw new IllegalStateException("SEED_ADMIN_DISPLAY_NAME은 30자 이하여야 합니다.");
		}
	}
}
