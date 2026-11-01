package com.autolog.server.trip;

import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.response.ApiResponse;
import com.autolog.server.global.response.PageResponse;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.global.security.DevicePrincipal;
import com.autolog.server.trip.TripDtos.TripBatchRequest;
import com.autolog.server.trip.TripDtos.TripBatchResponse;
import com.autolog.server.trip.TripDtos.TripEditRequest;
import com.autolog.server.trip.TripViews.EditResult;
import com.autolog.server.trip.TripViews.TripDetail;
import com.autolog.server.trip.TripViews.TripListItem;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운행 API (docs/contracts/api/trips.md) */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

	private final TripUploadService uploadService;
	private final TripEditService editService;
	private final TripQueryService queryService;

	public TripController(TripUploadService uploadService, TripEditService editService,
			TripQueryService queryService) {
		this.uploadService = uploadService;
		this.editService = editService;
		this.queryService = queryService;
	}

	/** 차량: 트립 일괄 업로드 (최대 50건, 건별 결과) */
	@PutMapping("/batch")
	public ApiResponse<TripBatchResponse> upload(@AuthenticationPrincipal DevicePrincipal device,
			@Valid @RequestBody TripBatchRequest request) {
		return ApiResponse.ok(uploadService.upload(device, request));
	}

	/** 차량·관리자: 운행 수정 */
	@PostMapping("/{clientUuid}/edits")
	public ApiResponse<EditResult> edit(Authentication authentication, @PathVariable String clientUuid,
			@RequestBody TripEditRequest request) {
		return switch (authentication.getPrincipal()) {
			case DevicePrincipal device -> ApiResponse.ok(editService.editByDevice(device, clientUuid, request));
			case AdminPrincipal admin -> ApiResponse.ok(editService.editByAdmin(admin, clientUuid, request));
			default -> throw new BusinessException(ErrorCode.FORBIDDEN);
		};
	}

	/** 관리자: 운행 목록 (마스킹, 시작 시각 내림차순) */
	@GetMapping
	public ApiResponse<PageResponse<TripListItem>> list(@AuthenticationPrincipal AdminPrincipal admin,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long vehicleId,
			@RequestParam(required = false) Long driverId,
			@RequestParam(required = false) String purpose,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(queryService.list(admin.orgId(),
				new TripQueryService.TripSearch(from, to, vehicleId, driverId, purpose, page, size)));
	}

	/** 관리자: 운행 상세 (마스킹, 차량 자동값·수정 이력 포함) */
	@GetMapping("/{clientUuid}")
	public ApiResponse<TripDetail> detail(@AuthenticationPrincipal AdminPrincipal admin,
			@PathVariable String clientUuid) {
		return ApiResponse.ok(queryService.detail(admin.orgId(), clientUuid));
	}
}
