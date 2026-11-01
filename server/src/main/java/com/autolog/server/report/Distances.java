package com.autolog.server.report;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 거리 합계·비율 계산 (km 소수 첫째 자리, 비율 % 소수 첫째 자리 반올림) */
final class Distances {

	static final BigDecimal ZERO_KM = BigDecimal.ZERO.setScale(1);
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private Distances() {
	}

	/** numerator ÷ total × 100. total이 0이면 null */
	static BigDecimal ratio(BigDecimal numerator, BigDecimal total) {
		if (total.signum() == 0) {
			return null;
		}
		return numerator.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
	}
}
