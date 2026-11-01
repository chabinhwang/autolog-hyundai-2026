package com.autolog.server.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.autolog.server.masterdata.Driver;
import com.autolog.server.masterdata.Vehicle;
import com.autolog.server.trip.TripViews.EditEntry;
import com.autolog.server.trip.TripViews.EditResult;
import com.autolog.server.trip.TripViews.FieldChange;
import com.autolog.server.trip.TripViews.TripDetail;
import com.autolog.server.trip.TripViews.TripListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 마스킹 규칙 (rules §7) 단위 테스트. 관리자 응답은 전부 TripViewMapper를 거친다. */
class TripViewMapperTest {

	private static final Instant STARTED = OffsetDateTime.parse("2026-11-03T08:12:00+09:00").toInstant();
	private static final Instant ENDED = OffsetDateTime.parse("2026-11-03T08:47:00+09:00").toInstant();

	private final TripViewMapper mapper = new TripViewMapper(JsonMapper.builder().build());
	private final Vehicle vehicle = new Vehicle(1L, "12가3456", "아이오닉 5", null, STARTED);
	private final Driver driver = new Driver(1L, "영업1팀", "김영업", STARTED);

	@Test
	void personalHidesPlacesNoteAndTimesButKeepsDateDriverAndOdometer() {
		Trip trip = trip(Purpose.PERSONAL, home(), client(), "병원 방문");
		TripListItem item = mapper.toListItem(trip, true);

		assertThat(item.masked()).isTrue();
		assertThat(item.origin()).isNull();
		assertThat(item.dest()).isNull();
		assertThat(item.note()).isNull();
		assertThat(item.startedAt()).isNull();
		assertThat(item.endedAt()).isNull();
		// 공개: 사용일자, 직원, 계기판 거리, 주행거리, 용도
		assertThat(item.tripDate()).hasToString("2026-11-03");
		assertThat(item.driver().name()).isEqualTo("김영업");
		assertThat(item.odoStartKm()).isEqualByComparingTo("12034.5");
		assertThat(item.odoEndKm()).isEqualByComparingTo("12051.2");
		assertThat(item.distanceKm()).isEqualByComparingTo("16.7");
		assertThat(item.purpose()).isEqualTo(Purpose.PERSONAL);
		assertThat(item.edited()).isTrue();
	}

	@Test
	void unconfirmedShowsOnlyCompanyAndClientLabels() {
		TripListItem homeToClient = mapper.toListItem(trip(Purpose.UNCONFIRMED, home(), client(), "메모"), false);
		assertThat(homeToClient.masked()).isTrue();
		assertThat(homeToClient.origin()).isNull();
		assertThat(homeToClient.dest()).isEqualTo(new TripViews.EndpointView(EndpointType.CLIENT, "한빛상사"));
		assertThat(homeToClient.note()).isNull();
		assertThat(homeToClient.startedAt()).isNull();
		assertThat(homeToClient.endedAt()).isNull();

		TripListItem companyToUnregistered = mapper.toListItem(
				trip(Purpose.UNCONFIRMED, company(), unregistered(), null), false);
		assertThat(companyToUnregistered.origin()).isEqualTo(new TripViews.EndpointView(EndpointType.COMPANY, "본사"));
		assertThat(companyToUnregistered.dest()).isNull();
	}

	@Test
	void commuteShowsEverythingWithHomeLabel() {
		TripListItem item = mapper.toListItem(trip(Purpose.COMMUTE, home(), company(), "메모"), false);
		assertThat(item.masked()).isFalse();
		assertThat(item.origin()).isEqualTo(new TripViews.EndpointView(EndpointType.HOME, "집"));
		assertThat(item.dest()).isEqualTo(new TripViews.EndpointView(EndpointType.COMPANY, "본사"));
		assertThat(item.note()).isEqualTo("메모");
		assertThat(item.startedAt()).isEqualTo(OffsetDateTime.parse("2026-11-03T08:12:00+09:00"));
		assertThat(item.startedAt().getOffset().getId()).isEqualTo("+09:00");
		assertThat(item.endedAt()).isEqualTo(OffsetDateTime.parse("2026-11-03T08:47:00+09:00"));
	}

	@Test
	void businessShowsEverything() {
		TripListItem item = mapper.toListItem(trip(Purpose.BUSINESS, unregistered(), client(), "분기 회의"), false);
		assertThat(item.masked()).isFalse();
		assertThat(item.origin()).isEqualTo(new TripViews.EndpointView(EndpointType.UNREGISTERED, "스타벅스"));
		assertThat(item.dest().label()).isEqualTo("한빛상사");
		assertThat(item.note()).isEqualTo("분기 회의");
		assertThat(item.startedAt()).isNotNull();
	}

	@Test
	void detailMasksHistoryNoteValuesAndRecordTimesForPersonal() {
		Trip trip = trip(Purpose.PERSONAL, home(), client(), "병원");
		String changes = mapper.writeChanges(List.of(
				new FieldChange("purpose", "UNCONFIRMED", "PERSONAL"),
				new FieldChange("note", null, "병원")));
		TripEditLog log = new TripEditLog(1L, 1, UUID.randomUUID(), ActorType.DEVICE, 7L, changes, null, ENDED, ENDED);
		TripDetail detail = mapper.toDetail(trip, List.of(new EditEntry(log, "12가3456 차량")));

		assertThat(detail.masked()).isTrue();
		assertThat(detail.note()).isNull();
		assertThat(detail.origin()).isNull();
		assertThat(detail.startedAt()).isNull();
		assertThat(detail.recordedAt()).isNull();
		assertThat(detail.syncedAt()).isNull();
		assertThat(detail.auto().purpose()).isEqualTo(Purpose.UNCONFIRMED);
		assertThat(detail.edits()).singleElement().satisfies(edit -> {
			assertThat(edit.actorName()).isEqualTo("12가3456 차량");
			assertThat(edit.changes()).containsExactly(
					new FieldChange("purpose", "UNCONFIRMED", "PERSONAL"),
					new FieldChange("note", null, null));
		});
	}

	@Test
	void detailKeepsHistoryNoteValuesForBusiness() {
		Trip trip = trip(Purpose.BUSINESS, home(), client(), "분기 실적 회의");
		String changes = mapper.writeChanges(List.of(new FieldChange("note", null, "분기 실적 회의")));
		TripEditLog log = new TripEditLog(1L, 1, null, ActorType.ADMIN, 1L, changes, "정정", ENDED, ENDED);
		TripDetail detail = mapper.toDetail(trip, List.of(new EditEntry(log, "총무팀")));

		assertThat(detail.masked()).isFalse();
		assertThat(detail.recordedAt()).isNotNull();
		assertThat(detail.edits().getFirst().changes())
				.containsExactly(new FieldChange("note", null, "분기 실적 회의"));
		assertThat(detail.edits().getFirst().reason()).isEqualTo("정정");
	}

	@Test
	void editResultIsMaskedOnlyForAdmin() {
		Trip trip = trip(Purpose.UNCONFIRMED, home(), client(), "운전자 메모");
		String changes = mapper.writeChanges(List.of(new FieldChange("note", "이전", "운전자 메모")));
		TripEditLog log = new TripEditLog(1L, 2, null, ActorType.DEVICE, 1L, changes, null, ENDED, ENDED);

		EditResult forAdmin = mapper.toEditResult(trip, log, true);
		assertThat(forAdmin.current().note()).isNull();
		assertThat(forAdmin.edit().changes()).containsExactly(new FieldChange("note", null, null));

		EditResult forDevice = mapper.toEditResult(trip, log, false);
		assertThat(forDevice.current().note()).isEqualTo("운전자 메모");
		assertThat(forDevice.edit().changes()).containsExactly(new FieldChange("note", "이전", "운전자 메모"));
	}

	private Trip trip(Purpose purpose, Trip.Endpoint origin, Trip.Endpoint dest, String note) {
		return Trip.create(UUID.randomUUID(), 1L, vehicle, 1L, driver, STARTED, ENDED, new BigDecimal("12034.5"),
				new BigDecimal("12051.2"), origin, dest, Purpose.UNCONFIRMED, purpose,
				purpose == Purpose.UNCONFIRMED ? PurposeSource.AUTO : PurposeSource.VOICE,
				purpose == Purpose.BUSINESS ? BusinessCategory.MEETING : null, note, ENDED, ENDED, "0.1.0");
	}

	private static Trip.Endpoint home() {
		return new Trip.Endpoint(EndpointType.HOME, null, "집");
	}

	private static Trip.Endpoint company() {
		return new Trip.Endpoint(EndpointType.COMPANY, 1L, "본사");
	}

	private static Trip.Endpoint client() {
		return new Trip.Endpoint(EndpointType.CLIENT, 4L, "한빛상사");
	}

	private static Trip.Endpoint unregistered() {
		return new Trip.Endpoint(EndpointType.UNREGISTERED, null, "스타벅스");
	}
}
