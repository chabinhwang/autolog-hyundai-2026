package com.autolog.app.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * STT 완료 조각을 모아 한 응답으로 만든다. 예: "네" + "거래처 방문이에요" → "네 거래처 방문이에요".
 *
 * - 첫 조각이 오기 전 전체 시간([await]의 timeoutMs)이 지나면 무응답(null)
 * - 조각이 온 뒤 graceMs 동안 새 말이 시작되지 않으면 끝난 것으로 본다
 * - 새 말이 시작되면 다음 조각까지 기다린다(전체 시간 안에서)
 * - STT 오류([onFailure])가 오면 그때까지 받은 것으로 바로 끝낸다. 아무것도 없으면 [failedWithoutAnswer]
 * - 완료 조각이 오지 않은 말은 마지막 부분 결과([onPartial])로 대신한다. 음성 서비스가 크래시하면 오류 콜백도
 *   없이 갱신이 멈추므로, 부분 결과가 partialIdleMs 동안 바뀌지 않으면 끝난 것으로 본다(car-app.md §3).
 */
class SegmentCollector {
    private data class Snapshot(
        val segments: List<String>,
        val partial: String,
        val speaking: Boolean,
        val failed: Boolean,
        val version: Int,
    ) {
        val hasPartial get() = partial.isNotBlank()
        val answer: String? get() = (segments + listOfNotNull(partial.trim().ifEmpty { null })).joinToString(" ").ifEmpty { null }
    }

    private val state = MutableStateFlow(Snapshot(emptyList(), "", speaking = false, failed = false, version = 0))

    /** 응답 없이 STT 오류로 끝났는지. [await] 뒤에 읽는다. */
    val failedWithoutAnswer: Boolean
        get() = state.value.let { it.failed && it.answer == null }

    fun onSpeechStarted() {
        state.value = state.value.let { it.copy(speaking = true, version = it.version + 1) }
    }

    fun onPartial(text: String) {
        state.value = state.value.let { it.copy(partial = text, version = it.version + 1) }
    }

    fun onFinalSegment(text: String) {
        val trimmed = text.trim()
        state.value = state.value.let {
            it.copy(
                segments = if (trimmed.isEmpty()) it.segments else it.segments + trimmed,
                partial = "",
                speaking = false,
                version = it.version + 1,
            )
        }
    }

    fun onFailure() {
        state.value = state.value.let { it.copy(speaking = false, failed = true, version = it.version + 1) }
    }

    suspend fun await(timeoutMs: Long, graceMs: Long, partialIdleMs: Long = PARTIAL_IDLE_MS): String? {
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val settled = state.first { it.failed || it.hasPartial || (it.segments.isNotEmpty() && !it.speaking) }
                if (settled.failed) break
                // 마지막 갱신부터 쉼을 잰다. 그동안 아무 변화가 없으면 응답이 끝난 것이다.
                val quiet = if (settled.hasPartial) partialIdleMs else graceMs
                withTimeoutOrNull(quiet) { state.first { it.version != settled.version } } ?: break
                if (state.value.failed) break
            }
        }
        return state.value.answer
    }

    private companion object {
        const val PARTIAL_IDLE_MS = 3_000L
    }
}
