package com.autolog.app.ui

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 이번 달 요약의 주행 거리 표시. 세 자리마다 콤마, 소수점은 둘째 자리까지(반올림, 뒤의 0은 생략). */
object Distance {
    fun km(value: Double): String {
        val format = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
        format.roundingMode = RoundingMode.HALF_UP
        return "${format.format(value)}km"
    }
}
