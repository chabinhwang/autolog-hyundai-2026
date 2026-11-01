package com.autolog.server.masterdata;

import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.MasterDataDtos.DriverCreateRequest;
import com.autolog.server.masterdata.MasterDataDtos.DriverPatchRequest;
import com.autolog.server.masterdata.MasterDataDtos.DriverView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;
	private final Clock clock;

	public DriverService(DriverRepository driverRepository, VehicleRepository vehicleRepository, Clock clock) {
		this.driverRepository = driverRepository;
		this.vehicleRepository = vehicleRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<DriverView> list(Long orgId, boolean includeInactive) {
		List<Driver> drivers = includeInactive
				? driverRepository.findByOrgIdOrderByIdAsc(orgId)
				: driverRepository.findByOrgIdAndActiveTrueOrderByIdAsc(orgId);
		return drivers.stream().map(DriverView::of).toList();
	}

	@Transactional
	public DriverView create(Long orgId, DriverCreateRequest request) {
		Driver driver = driverRepository.save(
				new Driver(orgId, request.dept().trim(), request.name().trim(), Times.now(clock)));
		return DriverView.of(driver);
	}

	@Transactional
	public DriverView patch(Long orgId, Long driverId, DriverPatchRequest request) {
		Driver driver = driverRepository.findByIdAndOrgId(driverId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_NOT_FOUND));
		Instant now = Times.now(clock);
		if (request.hasDept()) {
			driver.changeDept(requireText("dept", request.getDept()));
		}
		if (request.hasName()) {
			driver.changeName(requireText("name", request.getName()));
		}
		if (request.hasActive()) {
			if (request.getActive() == null) {
				throw BusinessException.validation("active", "null일 수 없습니다.");
			}
			driver.changeActive(request.getActive());
			if (!request.getActive()) {
				// 비활성 직원이 기본 운전자인 차량은 기본 운전자를 해제한다.
				for (Vehicle vehicle : vehicleRepository.findByDefaultDriver(driver)) {
					vehicle.changeDefaultDriver(null);
					vehicle.touch(now);
				}
			}
		}
		driver.touch(now);
		return DriverView.of(driver);
	}

	static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw BusinessException.validation(field, "비어 있을 수 없습니다.");
		}
		return value.trim();
	}
}
