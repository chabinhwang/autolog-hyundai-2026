package com.autolog.server.masterdata;

import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.MasterDataDtos.PlaceCreateRequest;
import com.autolog.server.masterdata.MasterDataDtos.PlaceDeletedView;
import com.autolog.server.masterdata.MasterDataDtos.PlacePatchRequest;
import com.autolog.server.masterdata.MasterDataDtos.PlaceView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {

	/** 장소 반경 기본값 (m) */
	public static final int DEFAULT_RADIUS_M = 200;

	private final PlaceRepository placeRepository;
	private final Clock clock;

	public PlaceService(PlaceRepository placeRepository, Clock clock) {
		this.placeRepository = placeRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PlaceView> list(Long orgId, PlaceType type) {
		List<Place> places = type == null
				? placeRepository.findByOrgIdAndDeletedFalseOrderByIdAsc(orgId)
				: placeRepository.findByOrgIdAndTypeAndDeletedFalseOrderByIdAsc(orgId, type);
		return places.stream().map(PlaceService::toView).toList();
	}

	@Transactional
	public PlaceView create(Long orgId, PlaceCreateRequest request) {
		Place place = new Place(orgId, request.name().trim(), PlaceType.valueOf(request.type()),
				coordinate(request.lat()), coordinate(request.lng()),
				request.radiusM() == null ? DEFAULT_RADIUS_M : request.radiusM(), Times.now(clock));
		return toView(placeRepository.save(place));
	}

	@Transactional
	public PlaceView patch(Long orgId, Long placeId, PlacePatchRequest request) {
		Place place = findActive(orgId, placeId);
		if (request.hasName()) {
			place.changeName(DriverService.requireText("name", request.getName()));
		}
		if (request.hasType()) {
			place.changeType(PlaceType.valueOf(requireValue("type", request.getType())));
		}
		if (request.hasLat()) {
			place.changeLat(coordinate(requireValue("lat", request.getLat())));
		}
		if (request.hasLng()) {
			place.changeLng(coordinate(requireValue("lng", request.getLng())));
		}
		if (request.hasRadiusM()) {
			place.changeRadiusM(requireValue("radiusM", request.getRadiusM()));
		}
		place.touch(Times.now(clock));
		return toView(place);
	}

	@Transactional
	public PlaceDeletedView delete(Long orgId, Long placeId) {
		Place place = findActive(orgId, placeId);
		place.delete();
		place.touch(Times.now(clock));
		return new PlaceDeletedView(place.getId(), true);
	}

	private Place findActive(Long orgId, Long placeId) {
		return placeRepository.findByIdAndOrgIdAndDeletedFalse(placeId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
	}

	public static PlaceView toView(Place place) {
		return new PlaceView(place.getId(), place.getName(), place.getType(), place.getLat().doubleValue(),
				place.getLng().doubleValue(), place.getRadiusM(), Times.toSeoul(place.getUpdatedAt()));
	}

	/** numeric(9,6)에 맞게 소수 여섯째 자리로 반올림 */
	private static BigDecimal coordinate(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private static <T> T requireValue(String field, T value) {
		if (value == null) {
			throw BusinessException.validation(field, "null일 수 없습니다.");
		}
		return value;
	}
}
