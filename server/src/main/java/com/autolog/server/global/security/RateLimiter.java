package com.autolog.server.global.security;

import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 인메모리 고정 창(1분) 요청 제한. 서버 1대 운영 전제라 외부 저장소를 쓰지 않는다.
 * 재시작하면 기록이 사라지고, 여러 인스턴스로 늘리면 인스턴스별로 따로 센다.
 */
@Component
public class RateLimiter {

	/** 분당 허용 횟수 (rules §1, §2, D6) */
	public static final int LIMIT_PER_MINUTE = 10;

	private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** 한도를 넘으면 429 RATE_LIMITED */
	public void check(Bucket bucket, String key) {
		long now = clock.millis();
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.values().removeIf(window -> now - window.startMillis >= WINDOW_MILLIS);
		}
		Window window = windows.compute(bucket.name() + ':' + key, (k, current) ->
				current == null || now - current.startMillis >= WINDOW_MILLIS
						? new Window(now, 1)
						: new Window(current.startMillis, current.count + 1));
		if (window.count > LIMIT_PER_MINUTE) {
			throw new BusinessException(ErrorCode.RATE_LIMITED);
		}
	}

	/** 테스트·운영 점검용 초기화 */
	public void reset() {
		windows.clear();
	}

	public enum Bucket {
		/** 로그인: IP당 */
		LOGIN,
		/** 페어링 코드 발급: IP당 */
		PAIRING_CODE,
		/** 페어링: 관리자당 */
		PAIR
	}

	private record Window(long startMillis, int count) {
	}
}
