package com.autolog.server.global.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 시각 변환 규칙. 저장은 Instant(timestamptz), 응답은 Asia/Seoul 오프셋(+09:00) ISO-8601.
 */
public final class Times {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private Times() {
	}

	/** 서버가 만드는 시각은 초 단위로 내림해 응답 형식을 계약 예시와 맞춘다. */
	public static Instant now(Clock clock) {
		return clock.instant().truncatedTo(ChronoUnit.SECONDS);
	}

	public static OffsetDateTime toSeoul(Instant instant) {
		return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
	}

	public static LocalDate seoulDate(Instant instant) {
		return instant.atZone(SEOUL).toLocalDate();
	}

	/** Asia/Seoul 날짜의 시작 시각 */
	public static Instant startOfDay(LocalDate date) {
		return date.atStartOfDay(SEOUL).toInstant();
	}
}
