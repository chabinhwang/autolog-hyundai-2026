package com.autolog.app.voice

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentCollectorTest {
    @Test
    fun `완료 조각 없이 부분 결과가 멈추면 마지막 부분 결과를 쓴다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500, partialIdleMs = 3_000) }
        c.onSpeechStarted()
        c.onPartial("네 맞")
        delay(200)
        c.onPartial("네 맞아요")
        assertEquals("네 맞아요", result.await())
        assertEquals(3_200L, testScheduler.currentTime)
    }

    @Test
    fun `부분 결과 뒤 엔진 오류면 부분 결과를 쓰고 실패로 보지 않는다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500) }
        c.onPartial("응")
        c.onFailure()
        assertEquals("응", result.await())
        assertFalse(c.failedWithoutAnswer)
    }

    @Test
    fun `완료 조각이 오면 부분 결과는 버린다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500) }
        c.onPartial("네 맞")
        c.onFinalSegment("네 맞아요")
        assertEquals("네 맞아요", result.await())
    }

    @Test
    fun `두 번째 조각을 말하다 끊기면 앞 조각과 부분 결과를 잇는다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500, partialIdleMs = 3_000) }
        c.onFinalSegment("네")
        delay(500)
        c.onPartial("거래처 방문")
        assertEquals("네 거래처 방문", result.await())
    }

    @Test
    fun `조각 없이 엔진 오류가 나면 기다리지 않고 실패로 끝낸다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500) }
        delay(300)
        c.onFailure()
        assertNull(result.await())
        assertTrue(c.failedWithoutAnswer)
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun `조각을 받은 뒤의 오류는 받은 응답을 쓴다`() = runTest {
        val c = SegmentCollector()
        val result = async { c.await(timeoutMs = 10_000, graceMs = 1_500) }
        c.onFinalSegment("네 맞아요")
        c.onFailure()
        assertEquals("네 맞아요", result.await())
        assertFalse(c.failedWithoutAnswer)
    }

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
