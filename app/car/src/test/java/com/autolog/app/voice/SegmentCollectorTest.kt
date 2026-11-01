package com.autolog.app.voice

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentCollectorTest {
    @Test
    fun `쉼으로 나뉜 조각을 이어 붙인다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500) }
        c.onSpeechStarted()
        c.onFinalSegment("네")
        delay(500)
        c.onSpeechStarted()
        delay(500)
        c.onFinalSegment("거래처 방문이에요")
        assertEquals("네 거래처 방문이에요", result.await())
    }

    @Test
    fun `조각 뒤 쉼이 이어지면 끝낸다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500) }
        c.onSpeechStarted()
        c.onFinalSegment("응")
        assertEquals("응", result.await())
        assertEquals(1_500, testScheduler.currentTime)
    }

    @Test
    fun `아무 말도 없으면 시간 초과 후 null`() = runTest {
        val c = SegmentCollector()
        assertNull(c.await(timeoutMs = 10_000, graceMs = 1_500))
        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun `빈 결과는 무시한다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 3_000, graceMs = 500) }
        c.onFinalSegment("  ")
        assertNull(result.await())
    }
}
