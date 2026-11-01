package com.autolog.server.trip;

import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.DriverRef;
import com.autolog.server.trip.TripViews.AutoValues;
import com.autolog.server.trip.TripViews.CurrentValues;
import com.autolog.server.trip.TripViews.EditEntry;
import com.autolog.server.trip.TripViews.EditResult;
import com.autolog.server.trip.TripViews.EditSummary;
import com.autolog.server.trip.TripViews.EditView;
import com.autolog.server.trip.TripViews.EndpointView;
import com.autolog.server.trip.TripViews.FieldChange;
import com.autolog.server.trip.TripViews.TripDetail;
import com.autolog.server.trip.TripViews.TripListItem;
import com.autolog.server.trip.TripViews.VehicleRef;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자에게 나가는 운행 값(JSON·XLSX)을 만드는 유일한 지점. 마스킹(rules §7, D4)은 여기서만 적용한다.
 * 저장값은 그대로 두고, 트립의 <b>현재</b> 용도를 기준으로 가린다.
 *
 * <table>
 * <tr><th>현재 용도</th><th>가리는 것 (null)</th></tr>
 * <tr><td>PERSONAL</td><td>출발·도착(유형·라벨), 메모, 시작·종료 시각, 이력의 메모 값</td></tr>
 * <tr><td>UNCONFIRMED</td><td>HOME·UNREGISTERED 출발·도착, 메모, 시작·종료 시각, 이력의 메모 값</td></tr>
 * <tr><td>COMMUTE·BUSINESS</td><td>없음 (HOME 라벨은 '집')</td></tr>
 * </table>
 * 가린 항목이 있으면 masked = true. 시각을 가리는 운행은 차량 저장·서버 수신 시각(recordedAt·syncedAt)도
 * 시:분을 드러내므로 함께 가린다. 계기판 거리·주행거리·사용일자·직원은 항상 공개한다.
 */
@Component
public class TripViewMapper {

	public static final String HOME_LABEL = "집";
	public static final String FIELD_NOTE = "note";

	private static final TypeReference<List<FieldChange>> CHANGES_TYPE = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	public TripViewMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** 현재 용도로 정해지는 마스킹 규칙 */
	public static Masking maskingOf(Purpose purpose) {
		return switch (purpose) {
			case PERSONAL -> Masking.PERSONAL;
			case UNCONFIRMED -> Masking.UNCONFIRMED;
			case COMMUTE, BUSINESS -> Masking.NONE;
		};
	}

	public TripListItem toListItem(Trip trip, boolean edited) {
		Masking masking = maskingOf(trip.getPurpose());
		return new TripListItem(trip.getClientUuid(), trip.getTripDate(), vehicleRef(trip),
				DriverRef.of(trip.getDriver()),
				masking.hidesTimes() ? null : Times.toSeoul(trip.getStartedAt()),
				masking.hidesTimes() ? null : Times.toSeoul(trip.getEndedAt()),
				trip.getOdoStartKm(), trip.getOdoEndKm(), trip.getDistanceKm(),
				endpoint(trip.getOrigin(), masking), endpoint(trip.getDest(), masking),
				trip.getPurpose(), trip.getPurposeSource(), trip.getBusinessCategory(),
				masking.hidesNote() ? null : trip.getNote(), edited, masking.masked());
	}

	public TripDetail toDetail(Trip trip, List<EditEntry> edits) {
		Masking masking = maskingOf(trip.getPurpose());
		List<EditView> editViews = edits.stream()
				.map(entry -> new EditView(entry.log().getSeq(), entry.log().getActorType(), entry.actorName(),
						maskChanges(readChanges(entry.log().getChanges()), masking), entry.log().getReason(),
						Times.toSeoul(entry.log().getEditedAt()), Times.toSeoul(entry.log().getCreatedAt())))
				.toList();
		return new TripDetail(trip.getClientUuid(), trip.getTripDate(), vehicleRef(trip),
				DriverRef.of(trip.getDriver()),
				masking.hidesTimes() ? null : Times.toSeoul(trip.getStartedAt()),
				masking.hidesTimes() ? null : Times.toSeoul(trip.getEndedAt()),
				trip.getOdoStartKm(), trip.getOdoEndKm(), trip.getDistanceKm(),
				endpoint(trip.getOrigin(), masking), endpoint(trip.getDest(), masking),
				trip.getPurpose(), trip.getPurposeSource(), trip.getBusinessCategory(),
				masking.hidesNote() ? null : trip.getNote(), masking.masked(),
				new AutoValues(trip.getAutoPurpose()), editViews,
				masking.hidesTimes() ? null : Times.toSeoul(trip.getRecordedAt()),
				masking.hidesTimes() ? null : Times.toSeoul(trip.getSyncedAt()),
				trip.getAppVersion());
	}

	/**
	 * 수정 응답. 관리자에게 돌려줄 때는 마스킹을 적용하고(forAdmin), 차량(운전자 본인)에게는 그대로 돌려준다.
	 */
	public EditResult toEditResult(Trip trip, TripEditLog log, boolean forAdmin) {
		Masking masking = forAdmin ? maskingOf(trip.getPurpose()) : Masking.NONE;
		EditSummary edit = log == null ? null
				: new EditSummary(log.getSeq(), log.getActorType(), maskChanges(readChanges(log.getChanges()), masking));
		CurrentValues current = new CurrentValues(trip.getPurpose(), trip.getPurposeSource(),
				trip.getBusinessCategory(), masking.hidesNote() ? null : trip.getNote(),
				trip.getDriver() == null ? null : trip.getDriver().getId());
		return new EditResult(edit, current);
	}

	/** 수정 이력 JSON 직렬화 (저장용) */
	public String writeChanges(List<FieldChange> changes) {
		return jsonMapper.writeValueAsString(changes);
	}

	public List<FieldChange> readChanges(String json) {
		return jsonMapper.readValue(json, CHANGES_TYPE);
	}

	private static List<FieldChange> maskChanges(List<FieldChange> changes, Masking masking) {
		if (!masking.hidesNote()) {
			return changes;
		}
		return changes.stream()
				.map(change -> FIELD_NOTE.equals(change.field()) ? new FieldChange(FIELD_NOTE, null, null) : change)
				.toList();
	}

	private static EndpointView endpoint(Trip.Endpoint endpoint, Masking masking) {
		if (masking == Masking.PERSONAL) {
			return null;
		}
		if (masking == Masking.UNCONFIRMED && !endpoint.type().requiresPlace()) {
			return null;
		}
		String label = endpoint.type() == EndpointType.HOME ? HOME_LABEL : endpoint.label();
		return new EndpointView(endpoint.type(), label);
	}

	private static VehicleRef vehicleRef(Trip trip) {
		return new VehicleRef(trip.getVehicle().getId(), trip.getVehicle().getRegNo());
	}

	/** 마스킹 수준 */
	public enum Masking {
		NONE(false, false),
		UNCONFIRMED(true, true),
		PERSONAL(true, true);

		private final boolean hidesTimes;
		private final boolean hidesNote;

		Masking(boolean hidesTimes, boolean hidesNote) {
			this.hidesTimes = hidesTimes;
			this.hidesNote = hidesNote;
		}

		public boolean hidesTimes() {
			return hidesTimes;
		}

		public boolean hidesNote() {
			return hidesNote;
		}

		public boolean masked() {
			return this != NONE;
		}
	}
}
