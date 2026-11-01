package com.autolog.app

import android.app.Application
import android.content.Context
import com.autolog.app.data.ConsentRepository
import com.autolog.app.data.TripRepository
import com.autolog.app.data.local.AutologDatabase
import com.autolog.app.data.remote.ApiFactory
import com.autolog.app.data.remote.AutologApi
import com.autolog.app.data.settings.SettingsStore
import com.autolog.app.sync.SyncEngine
import com.autolog.app.sync.SyncWorker
import com.autolog.app.voice.PleosVoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class AutologApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            // 질문 중에 앱이 꺼졌던 트립은 미확인으로 업로드 대상에 돌린다.
            container.database.tripDao().releaseStaleAnswers()
            SyncWorker.requestNow(this@AutologApp)
        }
        SyncWorker.schedulePeriodic(this)
    }
}

/** 앱 전체에서 하나만 쓰는 객체 모음(수동 DI). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AutologDatabase = AutologDatabase.create(appContext)
    val settings = SettingsStore(appContext)

    /** OkHttp 스레드에서 호출된다. 요청 수가 적어 매번 복호화한다(폐기·재페어링 즉시 반영). */
    private fun currentToken(): String? = runBlocking { settings.deviceToken() }

    val api: AutologApi = ApiFactory.create("${BuildConfig.API_BASE_URL}/api/v1/", ::currentToken)

    /** 기록 서비스가 알려주는 상태. 화면에서 주행 중 여부·마지막 안내를 보여준다. */
    val recorderStatus = kotlinx.coroutines.flow.MutableStateFlow(com.autolog.app.trip.TripRecorder.Status(driving = false, lastMessage = null))

    val tripRepository = TripRepository(database.tripDao(), database.tripEditDao(), database.placeDao())
    val consentRepository = ConsentRepository(database.consentDao(), settings)

    val syncEngine = SyncEngine(
        api = api,
        settings = settings,
        tripDao = database.tripDao(),
        editDao = database.tripEditDao(),
        placeDao = database.placeDao(),
        driverDao = database.driverDao(),
        consentDao = database.consentDao(),
        appVersion = BuildConfig.VERSION_NAME,
    )

    /**
     * Pleos TTS/STT는 프로세스에 하나만 둔다. 화면(페어링 완료 안내)과 기록 서비스가 각자 만들면
     * TTS 완료 콜백이 나중에 만든 쪽으로만 와서, 서비스가 질문 뒤 20초(타임아웃) 동안 듣지 못했다(AVD 확인).
     */
    val voice: PleosVoiceIO by lazy {
        PleosVoiceIO(appContext, debugAudio = if (BuildConfig.DEBUG) ::takeDebugAnswer else null)
    }

    /**
     * debug 빌드 전용: filesDir/voice_answer.pcm이 있으면 마이크 대신 그 음성을 한 번 넣고 지운다.
     * 마이크를 쓸 수 없는 개발 PC에서 도착 확인 흐름을 자동으로 시연·검증하기 위한 것.
     */
    private fun takeDebugAnswer(): ByteArray? {
        val file = File(appContext.filesDir, "voice_answer.pcm")
        if (!file.exists()) return null
        return file.readBytes().also { file.delete() }
    }
}
