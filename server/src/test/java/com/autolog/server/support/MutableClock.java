package com.autolog.server.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** 테스트용 시계: 고정 시각에서 시작해 필요한 만큼 앞으로 옮길 수 있다. */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now;

	public MutableClock(Instant start) {
		this.now = new AtomicReference<>(start);
	}

	public void set(Instant instant) {
		now.set(instant);
	}

	public void advance(Duration duration) {
		now.updateAndGet(current -> current.plus(duration));
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now.get();
	}
}
