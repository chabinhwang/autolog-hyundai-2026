package com.autolog.server.device;

import com.autolog.server.device.DeviceDtos.ConfigResponse;
import com.autolog.server.device.DeviceDtos.ConsentRequest;
import com.autolog.server.device.DeviceDtos.ConsentResponse;
import com.autolog.server.device.DeviceDtos.PairRequest;
import com.autolog.server.device.DeviceDtos.PairResponse;
import com.autolog.server.device.DeviceDtos.PairingCodeRequest;
import com.autolog.server.device.DeviceDtos.PairingCodeResponse;
import com.autolog.server.device.DeviceDtos.PairingStatusResponse;
import com.autolog.server.device.DeviceDtos.UnpairResponse;
import com.autolog.server.global.response.ApiResponse;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.global.security.DevicePrincipal;
import com.autolog.server.global.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 페어링·기기 API (docs/contracts/api/devices.md) */
@RestController
@RequestMapping("/api/v1")
public class DeviceController {

	private final PairingService pairingService;
	private final DeviceService deviceService;
	private final RateLimiter rateLimiter;

	public DeviceController(PairingService pairingService, DeviceService deviceService, RateLimiter rateLimiter) {
		this.pairingService = pairingService;
		this.deviceService = deviceService;
		this.rateLimiter = rateLimiter;
	}

	/** 차량: 페어링 코드 발급. 인증 없음, IP당 분당 10회 */
	@PostMapping("/devices/pairing-codes")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PairingCodeResponse> issuePairingCode(@Valid @RequestBody PairingCodeRequest request,
			HttpServletRequest http) {
		rateLimiter.check(RateLimiter.Bucket.PAIRING_CODE, http.getRemoteAddr());
		return ApiResponse.ok(pairingService.issue(request));
	}

	/** 차량: 페어링 상태 조회. 인증 없음 */
	@GetMapping("/devices/pairing-codes/{pairingId}")
	public ApiResponse<PairingStatusResponse> pairingStatus(@PathVariable String pairingId) {
		return ApiResponse.ok(pairingService.status(pairingId));
	}

	/** 관리자: 차량 페어링. 관리자당 분당 10회 */
	@PostMapping("/devices/pair")
	public ApiResponse<PairResponse> pair(@AuthenticationPrincipal AdminPrincipal admin,
			@Valid @RequestBody PairRequest request) {
		rateLimiter.check(RateLimiter.Bucket.PAIR, String.valueOf(admin.adminUserId()));
		return ApiResponse.ok(pairingService.pair(admin.orgId(), request));
	}

	/** 관리자: 차량 연결 해제 */
	@DeleteMapping("/vehicles/{vehicleId}/device")
	public ApiResponse<UnpairResponse> unpair(@AuthenticationPrincipal AdminPrincipal admin,
			@PathVariable Long vehicleId) {
		return ApiResponse.ok(pairingService.unpair(admin.orgId(), vehicleId));
	}

	/** 차량: 설정 내려받기 */
	@GetMapping("/devices/me/config")
	public ApiResponse<ConfigResponse> config(@AuthenticationPrincipal DevicePrincipal device,
			@RequestParam(required = false) String since) {
		return ApiResponse.ok(deviceService.config(device, since));
	}

	/** 차량: 직원 동의 기록. 처음이면 201, 이미 있으면 200 */
	@PostMapping("/devices/me/consents")
	public ResponseEntity<ApiResponse<ConsentResponse>> recordConsent(@AuthenticationPrincipal DevicePrincipal device,
			@Valid @RequestBody ConsentRequest request) {
		DeviceService.ConsentResult result = deviceService.recordConsent(device, request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.ok(result.response()));
	}
}
