package com.autolog.server.masterdata;

import com.autolog.server.global.response.ApiResponse;
import com.autolog.server.global.security.AdminPrincipal;
import com.autolog.server.masterdata.MasterDataDtos.DriverCreateRequest;
import com.autolog.server.masterdata.MasterDataDtos.DriverPatchRequest;
import com.autolog.server.masterdata.MasterDataDtos.DriverView;
import com.autolog.server.masterdata.MasterDataDtos.PlaceCreateRequest;
import com.autolog.server.masterdata.MasterDataDtos.PlaceDeletedView;
import com.autolog.server.masterdata.MasterDataDtos.PlacePatchRequest;
import com.autolog.server.masterdata.MasterDataDtos.PlaceView;
import com.autolog.server.masterdata.MasterDataDtos.VehicleCreateRequest;
import com.autolog.server.masterdata.MasterDataDtos.VehiclePatchRequest;
import com.autolog.server.masterdata.MasterDataDtos.VehicleView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 기준정보 API: 차량·직원·장소. 전부 관리자 JWT, 범위는 JWT의 orgId */
@RestController
@RequestMapping("/api/v1")
public class MasterDataController {

	private final VehicleService vehicleService;
	private final DriverService driverService;
	private final PlaceService placeService;

	public MasterDataController(VehicleService vehicleService, DriverService driverService,
			PlaceService placeService) {
		this.vehicleService = vehicleService;
		this.driverService = driverService;
		this.placeService = placeService;
	}

	@GetMapping("/vehicles")
	public ApiResponse<List<VehicleView>> listVehicles(@AuthenticationPrincipal AdminPrincipal admin,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return ApiResponse.ok(vehicleService.list(admin.orgId(), includeInactive));
	}

	@PostMapping("/vehicles")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<VehicleView> createVehicle(@AuthenticationPrincipal AdminPrincipal admin,
			@Valid @RequestBody VehicleCreateRequest request) {
		return ApiResponse.ok(vehicleService.create(admin.orgId(), request));
	}

	@PatchMapping("/vehicles/{vehicleId}")
	public ApiResponse<VehicleView> patchVehicle(@AuthenticationPrincipal AdminPrincipal admin,
			@PathVariable Long vehicleId, @Valid @RequestBody VehiclePatchRequest request) {
		return ApiResponse.ok(vehicleService.patch(admin.orgId(), vehicleId, request));
	}

	@GetMapping("/drivers")
	public ApiResponse<List<DriverView>> listDrivers(@AuthenticationPrincipal AdminPrincipal admin,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return ApiResponse.ok(driverService.list(admin.orgId(), includeInactive));
	}

	@PostMapping("/drivers")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<DriverView> createDriver(@AuthenticationPrincipal AdminPrincipal admin,
			@Valid @RequestBody DriverCreateRequest request) {
		return ApiResponse.ok(driverService.create(admin.orgId(), request));
	}

	@PatchMapping("/drivers/{driverId}")
	public ApiResponse<DriverView> patchDriver(@AuthenticationPrincipal AdminPrincipal admin,
			@PathVariable Long driverId, @Valid @RequestBody DriverPatchRequest request) {
		return ApiResponse.ok(driverService.patch(admin.orgId(), driverId, request));
	}

	@GetMapping("/places")
	public ApiResponse<List<PlaceView>> listPlaces(@AuthenticationPrincipal AdminPrincipal admin,
			@RequestParam(required = false) PlaceType type) {
		return ApiResponse.ok(placeService.list(admin.orgId(), type));
	}

	@PostMapping("/places")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PlaceView> createPlace(@AuthenticationPrincipal AdminPrincipal admin,
			@Valid @RequestBody PlaceCreateRequest request) {
		return ApiResponse.ok(placeService.create(admin.orgId(), request));
	}

	@PatchMapping("/places/{placeId}")
	public ApiResponse<PlaceView> patchPlace(@AuthenticationPrincipal AdminPrincipal admin,
			@PathVariable Long placeId, @Valid @RequestBody PlacePatchRequest request) {
		return ApiResponse.ok(placeService.patch(admin.orgId(), placeId, request));
	}

	@DeleteMapping("/places/{placeId}")
	public ApiResponse<PlaceDeletedView> deletePlace(@AuthenticationPrincipal AdminPrincipal admin,
			@PathVariable Long placeId) {
		return ApiResponse.ok(placeService.delete(admin.orgId(), placeId));
	}
}
