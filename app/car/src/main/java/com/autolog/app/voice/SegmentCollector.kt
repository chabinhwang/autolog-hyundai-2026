package com.autolog.app.voice

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * STT 완료 조각을 모아 한 응답으로 만든다. 예: "네" + "거래처 방문이에요" → "네 거래처 방문이에요".
 *
 * - 첫 조각이 오기 전 전체 시간([await]의 timeoutMs)이 지나면 무응답(null)
 * - 조각이 온 뒤 [graceMs] 동안 새 말이 시작되지 않으면 끝난 것으로 본다
 * - 새 말이 시작되면 다음 조각까지 기다린다(전체 시간 안에서)
 * - STT 오류([onFailure])가 오면 그때까지 받은 조각으로 바로 끝낸다. 조각이 없으면 [failedWithoutAnswer]
 */
class SegmentCollector {
    private data class Snapshot(val segments: List<String>, val speaking: Boolean, val failed: Boolean, val version: Int)

    private val state = MutableStateFlow(Snapshot(emptyList(), speaking = false, failed = false, version = 0))

    /** 응답 조각 없이 STT 오류로 끝났는지. [await] 뒤에 읽는다. */
    val failedWithoutAnswer: Boolean
        get() = state.value.let { it.failed && it.segments.isEmpty() }

    fun onSpeechStarted() {
        state.value = state.value.let { it.copy(speaking = true, version = it.version + 1) }
    }

    fun onFinalSegment(text: String) {
        val trimmed = text.trim()
        state.value = state.value.let {
            it.copy(
                segments = if (trimmed.isEmpty()) it.segments else it.segments + trimmed,
                speaking = false,
                version = it.version + 1,
            )
        }
    }

    fun onFailure() {
        state.value = state.value.let { it.copy(speaking = false, failed = true, version = it.version + 1) }
    }

    suspend fun await(timeoutMs: Long, graceMs: Long): String? {
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val settled = state.first { it.failed || (it.segments.isNotEmpty() && !it.speaking) }
                if (settled.failed) break
                delay(graceMs)
                val now = state.value
                if (now.version == settled.version || now.failed) break
            }
        }
        return state.value.segments.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
}
