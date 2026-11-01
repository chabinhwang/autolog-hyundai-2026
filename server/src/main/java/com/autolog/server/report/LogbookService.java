package com.autolog.server.report;

import com.autolog.server.auth.Organization;
import com.autolog.server.auth.OrganizationRepository;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.masterdata.Vehicle;
import com.autolog.server.masterdata.VehicleRepository;
import com.autolog.server.trip.TripQueryService;
import com.autolog.server.trip.TripRepository;
import com.autolog.server.trip.TripViewMapper;
import com.autolog.server.trip.TripViews.TripListItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기록부 내보내기 (rules §9, D7). 행 값은 TripViewMapper(마스킹)를 거친 값만 쓴다. */
@Service
public class LogbookService {

	public static final String FORMAT_XLSX = "xlsx";

	private final VehicleRepository vehicleRepository;
	private final OrganizationRepository organizationRepository;
	private final TripRepository tripRepository;
	private final TripViewMapper mapper;
	private final LogbookXlsxWriter writer;

	public LogbookService(VehicleRepository vehicleRepository, OrganizationRepository organizationRepository,
			TripRepository tripRepository, TripViewMapper mapper, LogbookXlsxWriter writer) {
		this.vehicleRepository = vehicleRepository;
		this.organizationRepository = organizationRepository;
		this.tripRepository = tripRepository;
		this.mapper = mapper;
		this.writer = writer;
	}

	@Transactional(readOnly = true)
	public LogbookFile export(Long orgId, Long vehicleId, LocalDate from, LocalDate to, String format) {
		if (format != null && !FORMAT_XLSX.equals(format)) {
			throw BusinessException.validation("format", "지원하는 형식은 xlsx입니다.");
		}
		TripQueryService.validateRange(from, to);
		// 비활성 차량도 과거 기록부는 내려받을 수 있다.
		Vehicle vehicle = vehicleRepository.findByIdAndOrgId(vehicleId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
		Organization organization = organizationRepository.findById(orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
		List<TripListItem> rows = tripRepository
				.findByOrgIdAndVehicleIdAndTripDateBetweenOrderByStartedAtAsc(orgId, vehicleId, from, to)
				.stream()
				.map(trip -> mapper.toListItem(trip, false))
				.toList();
		byte[] content = writer.write(new LogbookXlsxWriter.Header(from, to, organization.getName(),
				organization.getBizRegNo(), vehicle.getModelName(), vehicle.getRegNo()), rows);
		String filename = "운행기록부_" + vehicle.getRegNo() + "_" + from + "_" + to + ".xlsx";
		return new LogbookFile(filename, content);
	}

	public record LogbookFile(String filename, byte[] content) {
	}
}
