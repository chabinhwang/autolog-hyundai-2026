package com.autolog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    @Test
    fun `운행 목록은 서버와 같은 계기판 반올림 기준으로 거리를 보여준다`() {
        assertEquals("6.8km", Distance.tripKm(30012.5, 30019.25))
        assertEquals("4.2km", Distance.tripKm(30019.25, 30023.5))
        assertEquals("8.0km", Distance.tripKm(30023.5, 30031.5))
    }

    @Test
    fun `세 자리마다 콤마를 찍고 소수점 둘째 자리까지 보여준다`() {
        assertEquals("1,234.14km", Distance.km(1234.141))
    }

    @Test
    fun `소수점 셋째 자리에서 반올림한다`() {
        assertEquals("1,234.15km", Distance.km(1234.146))
    }

    @Test
    fun `소수점 뒤의 0은 생략한다`() {
        assertEquals("1,357.4km", Distance.km(1357.4))
        assertEquals("12km", Distance.km(12.0))
        assertEquals("0km", Distance.km(0.0))
    }

    @Test
    fun `백만 km 이상도 콤마로 끊는다`() {
        assertEquals("1,234,567.89km", Distance.km(1234567.891))
    }
}
