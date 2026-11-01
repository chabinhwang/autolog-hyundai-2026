package com.autolog.server.global.security;

import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 인메모리 요청 제한. 서버 1대 운영 전제라 외부 저장소를 쓰지 않는다.
 * 재시작하면 기록이 사라지고, 여러 인스턴스로 늘리면 인스턴스별로 따로 센다.
 * <ul>
 * <li>요청 수 제한: 고정 창(1분)에 10회 (로그인·페어링 코드 발급은 IP당, 페어링은 관리자당)</li>
 * <li>로그인 실패 제한: 계정(loginId)당 첫 실패부터 15분 안에 5회. IP와 달리 헤더 조작으로 우회할 수 없다 (rules §1)</li>
 * </ul>
 */
@Component
public class RateLimiter {

	/** 분당 허용 횟수 (rules §1, §2, D6) */
	public static final int LIMIT_PER_MINUTE = 10;
	/** 계정당 허용 로그인 실패 횟수 (rules §1) */
	public static final int LOGIN_FAILURE_LIMIT = 5;
	/** 로그인 실패를 세는 기간 (첫 실패부터) */
	public static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);

	static final String LOGIN_LOCKED_MESSAGE = "로그인 실패가 많아 잠시 로그인할 수 없습니다. 15분 후 다시 시도해 주세요.";

	private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
	private static final long LOGIN_FAILURE_WINDOW_MILLIS = LOGIN_FAILURE_WINDOW.toMillis();
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final Map<String, Window> loginFailures = new ConcurrentHashMap<>();

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** 한도를 넘으면 429 RATE_LIMITED */
	public void check(Bucket bucket, String key) {
		long now = clock.millis();
		cleanup(windows, WINDOW_MILLIS, now);
		Window window = windows.compute(bucket.name() + ':' + key, (k, current) ->
				current == null || now - current.startMillis >= WINDOW_MILLIS
						? new Window(now, 1)
						: new Window(current.startMillis, current.count + 1));
		if (window.count > LIMIT_PER_MINUTE) {
			throw new BusinessException(ErrorCode.RATE_LIMITED);
		}
	}

	/** 이 계정이 실패 한도에 걸려 있으면 429 RATE_LIMITED (비밀번호 확인 전에 호출) */
	public void checkLoginAllowed(String loginId) {
		long now = clock.millis();
		Window window = loginFailures.get(loginKey(loginId));
		if (window != null && now - window.startMillis < LOGIN_FAILURE_WINDOW_MILLIS
				&& window.count >= LOGIN_FAILURE_LIMIT) {
			throw new BusinessException(ErrorCode.RATE_LIMITED, LOGIN_LOCKED_MESSAGE);
		}
	}

	/** 로그인 실패 1회 기록. 기간이 지났으면 새로 센다 */
	public void recordLoginFailure(String loginId) {
		long now = clock.millis();
		cleanup(loginFailures, LOGIN_FAILURE_WINDOW_MILLIS, now);
		loginFailures.compute(loginKey(loginId), (k, current) ->
				current == null || now - current.startMillis >= LOGIN_FAILURE_WINDOW_MILLIS
						? new Window(now, 1)
						: new Window(current.startMillis, current.count + 1));
	}

	/** 로그인 성공 시 실패 기록 삭제 */
	public void clearLoginFailures(String loginId) {
		loginFailures.remove(loginKey(loginId));
	}

	/** 테스트·운영 점검용 초기화 */
	public void reset() {
		windows.clear();
		loginFailures.clear();
	}

	private static String loginKey(String loginId) {
		return loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
	}

	private static void cleanup(Map<String, Window> map, long windowMillis, long now) {
		if (map.size() > CLEANUP_THRESHOLD) {
			map.values().removeIf(window -> now - window.startMillis >= windowMillis);
		}
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
