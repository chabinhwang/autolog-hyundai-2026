package com.autolog.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** 응답 듣기 결과. 무응답과 STT 오류를 구분한다(오류일 때만 다시 묻는다, car-app.md §3). */
sealed interface ListenResult {
    data class Heard(val text: String) : ListenResult

    /** 시간 안에 응답 조각이 없었다. STT 초기화 실패도 여기에 넣는다(다시 물어도 같으므로). */
    data object NoAnswer : ListenResult

    /** 응답 조각 없이 STT 엔진이 오류를 알렸다. */
    data object EngineError : ListenResult
}

/** 음성 입출력. 도착 확인 질문(TTS)과 응답 인식(STT). */
interface VoiceIO {
    /** 다 읽을 때까지 기다린다. 실패하면 false. */
    suspend fun speak(text: String): Boolean

    /** 응답을 듣는다. */
    suspend fun listen(timeoutMs: Long): ListenResult

    fun release()
}

/**
 * Pleos TTS/STT SDK 3.3.1, ON_DEVICE 모드(앱 등록 불필요). 권한 TTS_SERVICE·STT_SERVICE.
 * Pleos Connect AVD 2.1.0에서 한국어 합성·인식 확인(2026-11-02).
 *
 * - TTS의 addEventListener는 initialize 성공 후에 불러야 한다(먼저 부르면 SDK 내부 예외로 앱 종료).
 * - STT 결과는 말 사이 쉼마다 completed=true 조각으로 나뉘어 오므로 조각을 모은다.
 *
 * @param debugAudio debug 빌드에서 마이크 대신 넣을 PCM(16kHz·16bit·mono)을 돌려준다. 자동 시연·테스트용.
 *   STT 오류로 끝나면 다시 물을 때 같은 PCM을 한 번 더 넣는다.
 *
 * 앱 전체에서 하나만 쓴다(AppContainer.voice). 화면과 기록 서비스가 함께 쓰므로 말하기·듣기를 한 번에 하나씩 처리한다.
 */
class PleosVoiceIO(
    private val context: Context,
    private val debugAudio: (() -> ByteArray?)? = null,
) : VoiceIO {
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var retryDebugPcm: ByteArray? = null
    private val busy = Mutex()

    private suspend fun ttsReady(): TextToSpeech? = tts ?: withContext(Dispatchers.Main) {
        val created = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
        val ok = CompletableDeferred<Boolean>()
        created.initialize(object : TtsInit {
            override fun onSuccess() {
                ok.complete(true)
            }

            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "TTS 초기화 실패: $errorType")
                ok.complete(false)
            }
        })
        if (withTimeoutOrNull(INIT_TIMEOUT_MS) { ok.await() } == true) {
            created.setLocale(Locale.KOREA)
            created.also { tts = it }
        } else {
            created.release()
            null
        }
    }

    override suspend fun speak(text: String): Boolean = busy.withLock { speakLocked(text) }

    private suspend fun speakLocked(text: String): Boolean {
        val engine = ttsReady() ?: return false
        val finished = CompletableDeferred<Boolean>()
        val listener = object : EventListener {
            override fun onReady() = Unit
            override fun onStart() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onDone() {
                finished.complete(true)
            }

            override fun onStop() {
                finished.complete(false)
            }

            override fun onError(errorType: TtsError) {
                Log.w(TAG, "TTS 오류: $errorType")
                finished.complete(false)
            }
        }
        var done = false
        try {
            withContext(Dispatchers.Main) {
                engine.addEventListener(listener)
                engine.speak(text, true)
            }
            return (withTimeoutOrNull(SPEAK_TIMEOUT_MS) { finished.await() } ?: false).also { done = true }
        } finally {
            // 도착 질문 팝업에서 버튼을 누르면 읽던 질문을 끊는다(취소).
            withContext(NonCancellable + Dispatchers.Main) {
                if (!done) engine.stop()
                engine.removeEventListener(listener)
            }
        }
    }

    private suspend fun sttReady(): SpeechToText? = stt ?: withContext(Dispatchers.Main) {
        val created = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
        val ok = CompletableDeferred<Boolean>()
        created.initialize(object : SttInit {
            override fun onSuccess() {
                ok.complete(true)
            }

            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "STT 초기화 실패: $errorType")
                ok.complete(false)
            }
        })
        if (withTimeoutOrNull(INIT_TIMEOUT_MS) { ok.await() } == true) {
            created.setLocale(Locale.KOREA)
            created.also { stt = it }
        } else {
            created.release()
            null
        }
    }

    override suspend fun listen(timeoutMs: Long): ListenResult = busy.withLock { listenLocked(timeoutMs) }

    private suspend fun listenLocked(timeoutMs: Long): ListenResult {
        val engine = sttReady() ?: return ListenResult.NoAnswer
        val collector = SegmentCollector()
        val listener = object : ResultListener {
            override fun onReady() = Unit
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onStartedRecognition() = collector.onSpeechStarted()
            override fun onUpdated(stt: String, completed: Boolean) {
                if (completed) collector.onFinalSegment(stt) else collector.onPartial(stt)
            }

            override fun onEndedRecognition() = Unit
            override fun onError(errorType: SttError) {
                Log.w(TAG, "STT 오류: $errorType")
                collector.onFailure()
            }
        }
        val pcm = retryDebugPcm ?: debugAudio?.invoke()
        retryDebugPcm = null
        val text = try {
            withContext(Dispatchers.Main) {
                engine.addListener(listener)
                engine.request()
            }
            pcm?.let { injectAudio(engine, it) }
            collector.await(timeoutMs, GRACE_AFTER_SEGMENT_MS)
        } finally {
            // 팝업 버튼으로 취소돼도 녹음을 멈춘다.
            withContext(NonCancellable + Dispatchers.Main) {
                engine.stop()
                engine.removeListener(listener)
            }
        }
        return when {
            text != null -> ListenResult.Heard(text)
            collector.failedWithoutAnswer -> ListenResult.EngineError.also { retryDebugPcm = pcm }
            else -> ListenResult.NoAnswer
        }
    }

    /** 실시간 속도(100ms당 3,200바이트)로 넣는다. ON_DEVICE 모드 전용 기능. */
    private suspend fun injectAudio(engine: SpeechToText, pcm: ByteArray) = withContext(Dispatchers.IO) {
        pcm.asList().chunked(3_200).forEach { chunk ->
            engine.sendAudio(chunk.toByteArray())
            Thread.sleep(100)
        }
        engine.completeAudioSend()
    }

    override fun release() {
        tts?.release()
        stt?.release()
        tts = null
        stt = null
    }

    private companion object {
        const val TAG = "AutoLogVoice"
        const val INIT_TIMEOUT_MS = 5_000L
        const val SPEAK_TIMEOUT_MS = 20_000L
        const val GRACE_AFTER_SEGMENT_MS = 1_500L
    }
}
