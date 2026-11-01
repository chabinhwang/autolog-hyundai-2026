package com.autolog.server.report;

import com.autolog.server.device.Device;
import com.autolog.server.device.DeviceRepository;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.DriverRef;
import com.autolog.server.masterdata.Vehicle;
import com.autolog.server.masterdata.VehicleRepository;
import com.autolog.server.report.ReportDtos.SummaryResponse;
import com.autolog.server.report.ReportDtos.Totals;
import com.autolog.server.report.ReportDtos.VehicleSummary;
import com.autolog.server.report.ReportDtos.Warning;
import com.autolog.server.trip.Trip;
import com.autolog.server.trip.TripRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월간 집계 (rules §8). Asia/Seoul trip_date 기준, 활성 차량별 */
@Service
public class DashboardService {

	/** D9 경고 기준값 */
	static final BigDecimal RATIO_HIGH_THRESHOLD = BigDecimal.valueOf(95);
	static final Duration SYNC_DELAY_THRESHOLD = Duration.ofHours(48);
	static final BigDecimal ODO_GAP_THRESHOLD_KM = BigDecimal.ONE;

	private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");

	private final VehicleRepository vehicleRepository;
	private final DeviceRepository deviceRepository;
	private final TripRepository tripRepository;
	private final Clock clock;

	public DashboardService(VehicleRepository vehicleRepository, DeviceRepository deviceRepository,
			TripRepository tripRepository, Clock clock) {
		this.vehicleRepository = vehicleRepository;
		this.deviceRepository = deviceRepository;
		this.tripRepository = tripRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public SummaryResponse summary(Long orgId, String monthParam) {
		YearMonth month = parseMonth(monthParam);
		List<Vehicle> vehicles = vehicleRepository.findByOrgIdAndActiveTrueOrderByIdAsc(orgId);
		List<Long> vehicleIds = vehicles.stream().map(Vehicle::getId).toList();
		Map<Long, List<Trip>> tripsByVehicle = tripRepository
				.findByOrgIdAndTripDateBetweenOrderByStartedAtAsc(orgId, month.atDay(1), month.atEndOfMonth())
				.stream()
				.collect(Collectors.groupingBy(trip -> trip.getVehicle().getId()));
		Map<Long, Device> activeDevices = vehicleIds.isEmpty() ? Map.of()
				: deviceRepository.findByVehicleIdInAndRevokedAtIsNull(vehicleIds).stream()
						.collect(Collectors.toMap(Device::getVehicleId, Function.identity()));
		Map<Long, Instant> lastSynced = deviceRepository.lastSyncedAtByVehicle(vehicleIds);
		Instant now = clock.instant();

		List<VehicleSummary> summaries = new ArrayList<>();
		Sums totals = new Sums();
		for (Vehicle vehicle : vehicles) {
			List<Trip> trips = tripsByVehicle.getOrDefault(vehicle.getId(), List.of());
			Sums sums = new Sums();
			trips.forEach(sums::add);
			trips.forEach(totals::add);
			Device device = activeDevices.get(vehicle.getId());
			List<Warning> warnings = new ArrayList<>();
			BigDecimal ratio = sums.businessRatio();
			if (ratio != null && ratio.compareTo(RATIO_HIGH_THRESHOLD) >= 0) {
				warnings.add(Warning.RATIO_HIGH);
			}
			if (sums.unconfirmedCount > 0) {
				warnings.add(Warning.UNCONFIRMED_EXISTS);
			}
			if (device != null) {
				Instant reference = device.getLastSyncedAt() != null ? device.getLastSyncedAt() : device.getPairedAt();
				if (Duration.between(reference, now).compareTo(SYNC_DELAY_THRESHOLD) > 0) {
					warnings.add(Warning.SYNC_DELAYED);
				}
			}
			if (hasOdoGap(trips)) {
				warnings.add(Warning.ODO_GAP);
			}
			summaries.add(new VehicleSummary(vehicle.getId(), vehicle.getRegNo(), vehicle.getModelName(),
					DriverRef.of(vehicle.getDefaultDriver()), device != null,
					Times.toSeoul(lastSynced.get(vehicle.getId())), trips.size(), sums.total, sums.commute,
					sums.business, sums.personal, sums.unconfirmed, sums.unconfirmedCount, ratio,
					sums.maxBusinessRatio(), warnings));
		}
		return new SummaryResponse(month.toString(), summaries, new Totals(totals.total, totals.commute,
				totals.business, totals.personal, totals.unconfirmed, totals.unconfirmedCount, totals.businessRatio()));
	}

	/** 시작 시각순 연속 트립에서 직전 주행 후 거리와 다음 주행 전 거리 차이가 1km 초과 */
	static boolean hasOdoGap(List<Trip> tripsByStartedAt) {
		for (int i = 1; i < tripsByStartedAt.size(); i++) {
			BigDecimal gap = tripsByStartedAt.get(i).getOdoStartKm()
					.subtract(tripsByStartedAt.get(i - 1).getOdoEndKm()).abs();
			if (gap.compareTo(ODO_GAP_THRESHOLD_KM) > 0) {
				return true;
			}
		}
		return false;
	}

	private static YearMonth parseMonth(String value) {
		if (value == null || !MONTH.matcher(value).matches()) {
			throw BusinessException.validation("month", "YYYY-MM 형식이어야 합니다.");
		}
		try {
			return YearMonth.parse(value);
		} catch (DateTimeParseException e) {
			throw BusinessException.validation("month", "YYYY-MM 형식이어야 합니다.");
		}
	}

	/** 용도별 거리 합계 */
	static final class Sums {

		BigDecimal total = Distances.ZERO_KM;
		BigDecimal commute = Distances.ZERO_KM;
		BigDecimal business = Distances.ZERO_KM;
		BigDecimal personal = Distances.ZERO_KM;
		BigDecimal unconfirmed = Distances.ZERO_KM;
		int unconfirmedCount;

		void add(Trip trip) {
			BigDecimal distance = trip.getDistanceKm();
			total = total.add(distance);
			switch (trip.getPurpose()) {
				case COMMUTE -> commute = commute.add(distance);
				case BUSINESS -> business = business.add(distance);
				case PERSONAL -> personal = personal.add(distance);
				case UNCONFIRMED -> {
					unconfirmed = unconfirmed.add(distance);
					unconfirmedCount++;
				}
			}
		}

		/** 업무사용비율 = (출퇴근 + 일반 업무) ÷ 총주행거리 × 100 */
		BigDecimal businessRatio() {
			return Distances.ratio(commute.add(business), total);
		}

		/** 최대 비율 = (출퇴근 + 일반 업무 + 미확인) ÷ 총주행거리 × 100 */
		BigDecimal maxBusinessRatio() {
			return Distances.ratio(commute.add(business).add(unconfirmed), total);
		}
	}
}
