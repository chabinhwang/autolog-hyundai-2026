package com.autolog.server.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.auth.AdminUser;
import com.autolog.server.auth.AdminUserRepository;
import com.autolog.server.auth.Organization;
import com.autolog.server.auth.OrganizationRepository;
import com.autolog.server.global.security.JwtService;
import com.autolog.server.global.security.RateLimiter;
import com.autolog.server.global.security.TokenHasher;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 통합 테스트 공통: Testcontainers PostgreSQL + 고정 시계 + MockMvc.
 * 테스트끼리 DB를 같이 쓰므로 각 테스트는 자기 회사(newOrg)를 만들어 데이터를 분리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class IntegrationTest {

	private static final AtomicInteger ORG_SEQ = new AtomicInteger();

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected JsonMapper jsonMapper;
	@Autowired
	protected MutableClock clock;
	@Autowired
	protected RateLimiter rateLimiter;
	@Autowired
	protected JwtService jwtService;
	@Autowired
	protected OrganizationRepository organizationRepository;
	@Autowired
	protected AdminUserRepository adminUserRepository;
	@Autowired
	protected PasswordEncoder passwordEncoder;

	@BeforeEach
	void resetSharedState() {
		clock.set(TestClockConfiguration.BASE_TIME.toInstant());
		rateLimiter.reset();
	}

	// ----- 회사·관리자 -----

	/** 새 회사 + 관리자 (비밀번호 password-1234) */
	protected TestOrg newOrg() {
		int seq = ORG_SEQ.incrementAndGet();
		String bizRegNo = String.format("%03d-%02d-%05d", 100 + seq % 800, seq % 100,
				ThreadLocalRandom.current().nextInt(100_000));
		while (organizationRepository.findByBizRegNo(bizRegNo).isPresent()) {
			bizRegNo = String.format("%03d-%02d-%05d", 100 + seq % 800, seq % 100,
					ThreadLocalRandom.current().nextInt(100_000));
		}
		Organization org = organizationRepository.save(new Organization("테스트회사" + seq, bizRegNo, clock.instant()));
		String loginId = "admin" + seq + "-" + UUID.randomUUID().toString().substring(0, 8) + "@autolog-test.kr";
		AdminUser admin = adminUserRepository.save(new AdminUser(org.getId(), loginId,
				passwordEncoder.encode("password-1234"), "총무팀" + seq, clock.instant()));
		String token = jwtService.issue(admin.getId(), org.getId()).value();
		return new TestOrg(org.getId(), admin.getId(), loginId, org.getName(), org.getBizRegNo(), token);
	}

	public record TestOrg(long orgId, long adminId, String loginId, String name, String bizRegNo, String token) {
	}

	// ----- 기준정보 -----

	protected long createDriver(TestOrg org, String dept, String name) throws Exception {
		return id(perform(post("/api/v1/drivers"), org.token(), Map.of("dept", dept, "name", name))
				.andExpect(status().isCreated()));
	}

	protected long createVehicle(TestOrg org, String regNo, String modelName) throws Exception {
		return id(perform(post("/api/v1/vehicles"), org.token(), Map.of("regNo", regNo, "modelName", modelName))
				.andExpect(status().isCreated()));
	}

	protected long createVehicle(TestOrg org, String regNo, String modelName, long defaultDriverId) throws Exception {
		return id(perform(post("/api/v1/vehicles"), org.token(),
				Map.of("regNo", regNo, "modelName", modelName, "defaultDriverId", defaultDriverId))
				.andExpect(status().isCreated()));
	}

	protected long createPlace(TestOrg org, String name, String type) throws Exception {
		return id(perform(post("/api/v1/places"), org.token(),
				Map.of("name", name, "type", type, "lat", 37.497942, "lng", 127.027621, "radiusM", 200))
				.andExpect(status().isCreated()));
	}

	// ----- 페어링 -----

	/** 기기 토큰 원문 (32바이트 base64url) */
	protected static String newDeviceToken() {
		byte[] bytes = new byte[32];
		ThreadLocalRandom.current().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	protected IssuedCode issuePairingCode(String deviceToken) throws Exception {
		JsonNode data = data(mockMvc.perform(post("/api/v1/devices/pairing-codes")
						.with(remoteAddr(randomIp()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("installationId", UUID.randomUUID().toString(),
								"tokenHash", TokenHasher.sha256Hex(deviceToken)))))
				.andExpect(status().isCreated()));
		return new IssuedCode(data.get("pairingId").asString(), data.get("code").asString());
	}

	public record IssuedCode(String pairingId, String code) {
	}

	/** 차량을 페어링하고 기기 토큰 원문을 돌려준다. */
	protected PairedDevice pair(TestOrg org, long vehicleId) throws Exception {
		String deviceToken = newDeviceToken();
		IssuedCode code = issuePairingCode(deviceToken);
		JsonNode data = data(perform(post("/api/v1/devices/pair"), org.token(),
				Map.of("code", code.code(), "vehicleId", vehicleId))
				.andExpect(status().isOk()));
		return new PairedDevice(deviceToken, data.get("deviceId").asLong(), vehicleId);
	}

	public record PairedDevice(String token, long deviceId, long vehicleId) {
	}

	// ----- 트립 -----

	/** 기본 업로드 트립 (출근, HOME → COMPANY). 필요한 값만 put으로 바꿔 쓴다. */
	protected Map<String, Object> trip(long vehicleId, OffsetDateTime startedAt, double odoStart, double odoEnd,
			String purpose) {
		Map<String, Object> trip = new LinkedHashMap<>();
		trip.put("clientUuid", UUID.randomUUID().toString());
		trip.put("vehicleId", vehicleId);
		trip.put("startedAt", startedAt.toString());
		trip.put("endedAt", startedAt.plusMinutes(35).toString());
		trip.put("odoStartKm", odoStart);
		trip.put("odoEndKm", odoEnd);
		trip.put("origin", endpoint("HOME", null, null));
		trip.put("dest", endpoint("UNREGISTERED", null, "스타벅스 강남점"));
		trip.put("autoPurpose", purpose);
		trip.put("purpose", purpose);
		trip.put("purposeSource", "UNCONFIRMED".equals(purpose) ? "AUTO" : "VOICE");
		trip.put("businessCategory", null);
		trip.put("note", null);
		trip.put("driverId", null);
		trip.put("recordedAt", startedAt.plusMinutes(38).toString());
		return trip;
	}

	protected static Map<String, Object> endpoint(String type, Long placeId, String label) {
		Map<String, Object> endpoint = new LinkedHashMap<>();
		endpoint.put("type", type);
		endpoint.put("placeId", placeId);
		endpoint.put("label", label);
		return endpoint;
	}

	protected ResultActions upload(PairedDevice device, List<Map<String, Object>> trips) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("appVersion", "0.1.0");
		body.put("trips", new ArrayList<>(trips));
		return perform(put("/api/v1/trips/batch"), device.token(), body);
	}

	/** 업로드 후 건별 결과 목록 */
	protected JsonNode uploadResults(PairedDevice device, List<Map<String, Object>> trips) throws Exception {
		return data(upload(device, trips).andExpect(status().isOk())).get("results");
	}

	// ----- 공통 -----

	protected ResultActions perform(MockHttpServletRequestBuilder builder, String token, Object body)
			throws Exception {
		if (token != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
		}
		return mockMvc.perform(builder);
	}

	protected ResultActions getWith(String token, String url) throws Exception {
		return perform(get(url), token, null);
	}

	protected ResultActions deleteWith(String token, String url) throws Exception {
		return perform(delete(url), token, null);
	}

	protected ResultActions patchWith(String token, String url, Object body) throws Exception {
		return perform(patch(url), token, body);
	}

	protected ResultActions postWith(String token, String url, Object body) throws Exception {
		return perform(post(url), token, body);
	}

	protected String json(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

	protected JsonNode body(ResultActions actions) throws Exception {
		MvcResult result = actions.andReturn();
		return jsonMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}

	protected JsonNode data(ResultActions actions) throws Exception {
		return body(actions).get("data");
	}

	protected long id(ResultActions actions) throws Exception {
		return data(actions).get("id").asLong();
	}

	protected static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
	}

	protected static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddr(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}
}
