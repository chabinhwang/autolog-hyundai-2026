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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
}
