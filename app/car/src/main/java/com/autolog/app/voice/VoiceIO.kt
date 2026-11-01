package com.autolog.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** 음성 입출력. 도착 확인 질문(TTS)과 응답 인식(STT). */
interface VoiceIO {
    /** 다 읽을 때까지 기다린다. 실패하면 false. */
    suspend fun speak(text: String): Boolean

    /** 응답을 듣는다. 무응답·실패면 null. */
    suspend fun listen(timeoutMs: Long): String?

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
 */
class PleosVoiceIO(
    private val context: Context,
    private val debugAudio: (() -> ByteArray?)? = null,
) : VoiceIO {
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null

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

    override suspend fun speak(text: String): Boolean {
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
        withContext(Dispatchers.Main) {
            engine.addEventListener(listener)
            engine.speak(text, true)
        }
        val result = withTimeoutOrNull(SPEAK_TIMEOUT_MS) { finished.await() } ?: false
        withContext(Dispatchers.Main) { engine.removeEventListener(listener) }
        return result
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

    override suspend fun listen(timeoutMs: Long): String? {
        val engine = sttReady() ?: return null
        val collector = SegmentCollector()
        val listener = object : ResultListener {
            override fun onReady() = Unit
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onStartedRecognition() = collector.onSpeechStarted()
            override fun onUpdated(stt: String, completed: Boolean) {
                if (completed) collector.onFinalSegment(stt)
            }

            override fun onEndedRecognition() = Unit
            override fun onError(errorType: SttError) {
                Log.w(TAG, "STT 오류: $errorType")
            }
        }
        withContext(Dispatchers.Main) {
            engine.addListener(listener)
            engine.request()
        }
        debugAudio?.invoke()?.let { pcm -> injectAudio(engine, pcm) }
        val text = collector.await(timeoutMs, GRACE_AFTER_SEGMENT_MS)
        withContext(Dispatchers.Main) {
            engine.stop()
            engine.removeListener(listener)
        }
        return text
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
