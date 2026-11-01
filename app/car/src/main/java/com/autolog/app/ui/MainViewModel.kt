package com.autolog.app.ui

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autolog.app.AutologApp
import com.autolog.app.data.local.DriverEntity
import com.autolog.app.data.local.PlaceEntity
import com.autolog.app.data.local.TripEntity
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.export.CsvExporter
import com.autolog.app.location.PleosLocationSource
import com.autolog.app.pairing.PairingController
import com.autolog.app.purpose.PurposeEstimator
import com.autolog.app.sync.SyncWorker
import com.autolog.app.trip.TripRecorderService
import com.autolog.app.voice.PleosVoiceIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/** 차량 앱 화면 상태. 화면은 정차 중에만 쓴다(DO 미선언 앱은 주행 중 플랫폼이 화면을 막는다). */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AutologApp).container
    private val db = container.database
    private val settings = container.settings
    private val voiceLazy = lazy { PleosVoiceIO(app) }
    private val voice by voiceLazy
    private val locationLazy = lazy { PleosLocationSource(app) }
    private val location by locationLazy

    val pairing = PairingController(
        api = container.api,
        settings = settings,
        scope = viewModelScope,
        onPaired = ::afterPaired,
    )

    val paired: StateFlow<Boolean?> = settings.paired.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val vehicle = settings.vehicle.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val drivers: StateFlow<List<DriverEntity>> = db.driverDao().observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentDriver: StateFlow<DriverEntity?> = combine(drivers, settings.currentDriverId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val recentTrips: StateFlow<List<TripEntity>> = db.tripDao().observeRecent(50).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unconfirmed: StateFlow<List<TripEntity>> = db.tripDao().observeUnconfirmed().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val failedCount: StateFlow<Int> = db.tripDao().observeFailedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pendingCount: StateFlow<Int> = db.tripDao().observePendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val places: StateFlow<List<PlaceEntity>> = db.placeDao().observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recorderStatus = container.recorderStatus

    /** 동의를 기다리는 직원. null이면 동의 화면을 닫는다. */
    private val _consentFor = MutableStateFlow<DriverEntity?>(null)
    val consentFor: StateFlow<DriverEntity?> = _consentFor.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    /** 앱을 열 때: 페어링돼 있으면 기록 서비스를 띄우고 동기화한다. */
    fun onAppVisible() {
        viewModelScope.launch {
            if (settings.deviceToken() != null) {
                TripRecorderService.start(getApplication())
                SyncWorker.requestNow(getApplication())
            }
        }
    }

    /** 재페어링 전 경고에 쓰는 미전송 건수 (D10). */
    suspend fun pendingBeforeRepair(): Int = pendingCount.first()

    fun startPairing() {
        SyncWorker.requestNow(getApplication())
        pairing.start()
    }

    private suspend fun afterPaired() {
        container.syncEngine.run()
        TripRecorderService.start(getApplication())
        voice.speak("차량 등록이 완료됐어요.")
    }

    fun selectDriver(driver: DriverEntity?) {
        viewModelScope.launch {
            if (driver != null && container.consentRepository.needsConsent(driver.id)) {
                _consentFor.value = driver
            } else {
                settings.setCurrentDriver(driver?.id)
            }
        }
    }

    fun acceptConsent() {
        val driver = _consentFor.value ?: return
        viewModelScope.launch {
            if (container.consentRepository.recordConsent(driver.id)) {
                settings.setCurrentDriver(driver.id)
                SyncWorker.requestNow(getApplication())
            } else {
                _message.value = "동의 문서 정보를 아직 받지 못했어요. 네트워크 연결 후 다시 시도해 주세요."
            }
            _consentFor.value = null
        }
    }

    /** 거부하면 그 직원은 이용자로 선택할 수 없다(rules §11). 이용자는 바꾸지 않는다. */
    fun declineConsent() {
        _consentFor.value = null
        _message.value = "동의하지 않으면 이 직원으로 운행을 기록할 수 없어요."
    }

    fun choosePurpose(trip: TripEntity, purpose: Purpose, category: BusinessCategory? = null) {
        viewModelScope.launch {
            container.tripRepository.changePurpose(trip.clientUuid, purpose, category)
            SyncWorker.requestNow(getApplication())
        }
    }

    /** 지금 위치를 집으로 등록한다. 집 위치는 차량 안에만 저장한다(D2). */
    fun registerHomeHere() {
        viewModelScope.launch {
            val point = location.lastKnown()
            if (point == null) {
                _message.value = "현재 위치를 알 수 없어요."
                return@launch
            }
            db.placeDao().deleteHome()
            db.placeDao().upsert(PlaceEntity(0, null, "집", EndpointType.HOME, point.lat, point.lng, HOME_RADIUS_M, false))
            _message.value = "지금 위치를 집으로 등록했어요. 이 위치는 서버로 보내지 않아요."
        }
    }

    /** 이번 달 운행을 CSV로 다운로드 폴더에 저장한다. */
    fun exportThisMonth() {
        viewModelScope.launch {
            val month = YearMonth.now(PurposeEstimator.SEOUL)
            val from = month.atDay(1).atStartOfDay(PurposeEstimator.SEOUL).toInstant()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(PurposeEstimator.SEOUL).toInstant()
            val trips = db.tripDao().between(from, to)
            val driverMap = drivers.value.associateBy { it.id }
            val csv = CsvExporter.export(trips, driverMap)
            val name = "autolog_${vehicle.value?.regNo ?: "vehicle"}_$month.csv"
            _message.value = withContext(Dispatchers.IO) {
                runCatching {
                    // 엑셀이 한글을 깨뜨리지 않도록 UTF-8 BOM을 붙인다.
                    val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray()
                    val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = getApplication<Application>().contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("저장 위치를 만들 수 없음")
                        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                        "다운로드 폴더"
                    } else {
                        // API 28은 MediaStore.Downloads가 없어 앱 전용 다운로드 폴더에 저장한다.
                        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("저장 위치 없음")
                        File(dir, name).writeBytes(bytes)
                        dir.absolutePath
                    }
                    "${location}에 $name 파일로 저장했어요. (${trips.size}건)"
                }.getOrElse { "CSV 저장에 실패했어요: ${it.message}" }
            }
        }
    }

    override fun onCleared() {
        if (voiceLazy.isInitialized()) voice.release()
        if (locationLazy.isInitialized()) location.release()
    }

    private companion object {
        const val HOME_RADIUS_M = 200
    }
}
