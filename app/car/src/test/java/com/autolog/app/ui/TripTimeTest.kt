package com.autolog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TripTimeTest {
    private fun kst(text: String) = Instant.parse(text.replace(" ", "T") + ":00+09:00")

    @Test
    fun `시작과 종료 시각을 KST로 보여준다`() {
        assertEquals("22:06–22:41", TripTime.range(kst("2026-11-02 22:06"), kst("2026-11-02 22:41")))
    }

    @Test
    fun `자정을 넘기면 종료 시각 앞에 다음날을 붙인다`() {
        assertEquals("23:50–다음날 00:20", TripTime.range(kst("2026-11-02 23:50"), kst("2026-11-03 00:20")))
    }

    @Test
    fun `걸린 시간은 분과 시간으로 줄여 쓴다`() {
        assertEquals("35분", TripTime.duration(kst("2026-11-02 22:06"), kst("2026-11-02 22:41")))
        assertEquals("1시간 5분", TripTime.duration(kst("2026-11-02 08:00"), kst("2026-11-02 09:05")))
        assertEquals("2시간", TripTime.duration(kst("2026-11-02 08:00"), kst("2026-11-02 10:00")))
        assertEquals("1분 미만", TripTime.duration(kst("2026-11-02 08:00"), Instant.parse("2026-11-01T23:00:30Z")))
    }
}
