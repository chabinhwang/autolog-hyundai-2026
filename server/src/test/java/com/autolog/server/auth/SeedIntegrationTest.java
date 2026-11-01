package com.autolog.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.global.config.AppProperties;
import com.autolog.server.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SeedIntegrationTest extends IntegrationTest {

	@Autowired
	private SeedInitializer seedInitializer;

	@Test
	void seedFromTestProfileCreatedOrganizationAndAdminAtStartup() throws Exception {
		Organization org = organizationRepository.findByBizRegNo("999-99-99999").orElseThrow();
		assertThat(org.getName()).isEqualTo("시드 주식회사");
		AdminUser admin = adminUserRepository.findByLoginId("seed-admin@autolog-test.kr").orElseThrow();
		assertThat(admin.getOrgId()).isEqualTo(org.getId());
		assertThat(admin.getDisplayName()).isEqualTo("시드관리자");
		assertThat(admin.getPasswordHash()).startsWith("$2");

		mockMvc.perform(post("/api/v1/auth/login")
						.with(remoteAddr(randomIp()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("loginId", "seed-admin@autolog-test.kr", "password", "seed-password-1234"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.organization.name").value("시드 주식회사"))
				.andExpect(jsonPath("$.data.admin.displayName").value("시드관리자"));
	}

	@Test
	void existingBizRegNoChangesNothing() {
		String hashBefore = adminUserRepository.findByLoginId("seed-admin@autolog-test.kr").orElseThrow()
				.getPasswordHash();
		boolean created = seedInitializer.seed(new AppProperties.Seed("이름 변경", "999-99-99999",
				"other-admin@autolog-test.kr", "new-password", "다른이름"));
		assertThat(created).isFalse();
		assertThat(organizationRepository.findByBizRegNo("999-99-99999").orElseThrow().getName())
				.isEqualTo("시드 주식회사");
		assertThat(adminUserRepository.findByLoginId("seed-admin@autolog-test.kr").orElseThrow().getPasswordHash())
				.isEqualTo(hashBefore);
		assertThat(adminUserRepository.findByLoginId("other-admin@autolog-test.kr")).isEmpty();
	}

	@Test
	void incompleteSeedIsSkipped() {
		assertThat(seedInitializer.seed(new AppProperties.Seed("새 회사", "888-88-88888", "new@autolog-test.kr",
				null, null))).isFalse();
		assertThat(organizationRepository.findByBizRegNo("888-88-88888")).isEmpty();
	}

	@Test
	void newBizRegNoCreatesAnotherOrganization() {
		assertThat(seedInitializer.seed(new AppProperties.Seed("심사용 회사", "777-77-77777",
				"review@autolog-test.kr", "review-password", null))).isTrue();
		AdminUser admin = adminUserRepository.findByLoginId("review@autolog-test.kr").orElseThrow();
		assertThat(admin.getDisplayName()).isEqualTo("관리자");
		assertThat(passwordEncoder.matches("review-password", admin.getPasswordHash())).isTrue();
	}

	@Test
	void malformedSeedValuesFail() {
		assertThatThrownBy(() -> seedInitializer.seed(new AppProperties.Seed("회사", "1234567890",
				"x@autolog-test.kr", "pw", null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SEED_BIZ_REG_NO");
	}
}
