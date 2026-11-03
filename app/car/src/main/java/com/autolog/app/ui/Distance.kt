package com.autolog.app.ui

import com.autolog.app.sync.TripMapper
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 주행 거리 표시. 이번 달 요약은 세 자리마다 콤마, 소수점 둘째 자리까지 표시한다. */
object Distance {
    /** 목록·기록부·API의 주행거리는 반올림한 두 계기판 값의 차이다. */
    fun tripKm(odoStartKm: Double, odoEndKm: Double): String = String.format(
        Locale.ROOT, "%.1fkm", TripMapper.roundKm(odoEndKm) - TripMapper.roundKm(odoStartKm),
    )

    fun km(value: Double): String {
        val format = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
        format.roundingMode = RoundingMode.HALF_UP
        return "${format.format(value)}km"
    }
}
