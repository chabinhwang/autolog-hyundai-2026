package com.autolog.server.masterdata;

import com.autolog.server.device.Device;
import com.autolog.server.device.DeviceRepository;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.MasterDataDtos.DeviceStatus;
import com.autolog.server.masterdata.MasterDataDtos.VehicleCreateRequest;
import com.autolog.server.masterdata.MasterDataDtos.VehicleView;
import com.autolog.server.masterdata.MasterDataDtos.VehiclePatchRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;
	private final DriverRepository driverRepository;
	private final DeviceRepository deviceRepository;
	private final Clock clock;

	public VehicleService(VehicleRepository vehicleRepository, DriverRepository driverRepository,
			DeviceRepository deviceRepository, Clock clock) {
		this.vehicleRepository = vehicleRepository;
		this.driverRepository = driverRepository;
		this.deviceRepository = deviceRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<VehicleView> list(Long orgId, boolean includeInactive) {
		List<Vehicle> vehicles = includeInactive
				? vehicleRepository.findByOrgIdOrderByIdAsc(orgId)
				: vehicleRepository.findByOrgIdAndActiveTrueOrderByIdAsc(orgId);
		return toViews(vehicles);
	}

	@Transactional
	public VehicleView create(Long orgId, VehicleCreateRequest request) {
		String regNo = request.regNo().trim();
		if (vehicleRepository.existsByOrgIdAndRegNo(orgId, regNo)) {
			throw new BusinessException(ErrorCode.VEHICLE_REG_NO_DUPLICATE);
		}
		Driver defaultDriver = request.defaultDriverId() == null ? null : activeDriver(orgId, request.defaultDriverId());
		Vehicle vehicle = new Vehicle(orgId, regNo, request.modelName().trim(), defaultDriver, Times.now(clock));
		try {
			vehicleRepository.saveAndFlush(vehicle);
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 번호가 들어온 경우
			throw new BusinessException(ErrorCode.VEHICLE_REG_NO_DUPLICATE);
		}
		return toViews(List.of(vehicle)).getFirst();
	}

	@Transactional
	public VehicleView patch(Long orgId, Long vehicleId, VehiclePatchRequest request) {
		Vehicle vehicle = vehicleRepository.findByIdAndOrgId(vehicleId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
		Instant now = Times.now(clock);
		if (request.hasRegNo()) {
			String regNo = DriverService.requireText("regNo", request.getRegNo());
			if (vehicleRepository.existsByOrgIdAndRegNoAndIdNot(orgId, regNo, vehicleId)) {
				throw new BusinessException(ErrorCode.VEHICLE_REG_NO_DUPLICATE);
			}
			vehicle.changeRegNo(regNo);
		}
		if (request.hasModelName()) {
			vehicle.changeModelName(DriverService.requireText("modelName", request.getModelName()));
		}
		if (request.hasDefaultDriverId()) {
			vehicle.changeDefaultDriver(request.getDefaultDriverId() == null
					? null : activeDriver(orgId, request.getDefaultDriverId()));
		}
		if (request.hasActive()) {
			if (request.getActive() == null) {
				throw BusinessException.validation("active", "null일 수 없습니다.");
			}
			vehicle.changeActive(request.getActive());
			if (!request.getActive()) {
				// 비활성 차량의 기기 토큰은 폐기한다.
				deviceRepository.findByVehicleIdAndRevokedAtIsNull(vehicleId).ifPresent(device -> device.revoke(now));
			}
		}
		vehicle.touch(now);
		try {
			vehicleRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.VEHICLE_REG_NO_DUPLICATE);
		}
		return toViews(List.of(vehicle)).getFirst();
	}

	/** 차량 목록 응답. 활성 기기와 마지막 동기화 시각(연결 해제된 기기 포함)을 붙인다. */
	List<VehicleView> toViews(List<Vehicle> vehicles) {
		List<Long> ids = vehicles.stream().map(Vehicle::getId).toList();
		Map<Long, Device> activeDevices = ids.isEmpty() ? Map.of()
				: deviceRepository.findByVehicleIdInAndRevokedAtIsNull(ids).stream()
						.collect(Collectors.toMap(Device::getVehicleId, Function.identity()));
		Map<Long, Instant> lastSynced = deviceRepository.lastSyncedAtByVehicle(ids);
		return vehicles.stream().map(vehicle -> {
			Device device = activeDevices.get(vehicle.getId());
			DeviceStatus status = new DeviceStatus(device != null, device == null ? null : device.getId(),
					device == null ? null : Times.toSeoul(device.getPairedAt()),
					Times.toSeoul(lastSynced.get(vehicle.getId())));
			return new VehicleView(vehicle.getId(), vehicle.getRegNo(), vehicle.getModelName(),
					DriverRef.of(vehicle.getDefaultDriver()), vehicle.isActive(), status);
		}).toList();
	}

	private Driver activeDriver(Long orgId, Long driverId) {
		return driverRepository.findByIdAndOrgIdAndActiveTrue(driverId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_NOT_FOUND));
	}
}
