package com.autolog.app.probe

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import kotlin.concurrent.thread

/**
 * debug 빌드 전용 플랫폼 점검 화면. Pleos AVD에서 SDK·네트워크가 실제로 동작하는지 확인하고
 * 결과를 화면과 logcat(태그 AutoLogProbe)에 남긴다.
 */
class ProbeActivity : ComponentActivity() {

    private val logs = mutableStateListOf<String>()
    private var vehicle: Vehicle? = null
    private var fusedLocation: FusedLocation? = null
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var navi: NaviHelper? = null
    private var naviConnected = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            log("권한 결과: $result")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::requestPermissions) { Text("권한") }
                            Button(onClick = ::probeVehicle) { Text("차량") }
                            Button(onClick = ::probeLocation) { Text("위치") }
                            Button(onClick = ::probeTts) { Text("TTS") }
                            Button(onClick = ::probeStt) { Text("STT") }
                            Button(onClick = ::probeNetwork) { Text("네트워크") }
                            Button(onClick = ::probeNavi) { Text("내비") }
                        }
                        LazyColumn(Modifier.padding(top = 12.dp)) {
                            items(logs) { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
        }
        runFromIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        runFromIntent(intent)
    }

    /** adb shell am start -n com.autolog.app/.probe.ProbeActivity --es run vehicle,network */
    private fun runFromIntent(intent: android.content.Intent?) {
        val names = intent?.getStringExtra("run")?.split(",")?.map { it.trim() } ?: return
        names.forEach { name ->
            when (name) {
                "permissions" -> requestPermissions()
                "vehicle" -> probeVehicle()
                "location" -> probeLocation()
                "tts" -> probeTts()
                "stt" -> probeStt()
                "network" -> probeNetwork()
                "sttfile" -> probeSttFile()
                "navi" -> probeNavi()
                "geocoder" -> probeGeocoder()
                "notify" -> probeNotify()
                "notifylater" -> {
                    log("5초 뒤 알림 게시")
                    android.os.Handler(mainLooper).postDelayed({ probeNotify() }, 5_000)
                }
                else -> log("알 수 없는 점검 항목: $name")
            }
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        runOnUiThread { logs.add(0, "${LocalTime.now().withNano(0)} $message") }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(FUSED_LOCATION_PERMISSION, Manifest.permission.RECORD_AUDIO),
        )
    }

    private fun probeVehicle() {
        val v = vehicle ?: Vehicle(this).also {
            it.initialize()
            vehicle = it
        }
        runCatching {
            v.getGear().getCurrentGearSelectionState(
                { gear -> log("기어 현재값: $gear") },
                { e -> log("기어 조회 실패: ${e.javaClass.simpleName} ${e.message}") },
            )
            v.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(selection: VehicleGear?) = log("기어 변경: $selection")
                override fun onFailed(e: Exception) = log("기어 구독 실패: ${e.message}")
            })
            v.getOdometer().getCurrentDistanceDriven(
                { km -> log("Odometer 현재값(km): $km") },
                { e -> log("Odometer 조회 실패: ${e.javaClass.simpleName} ${e.message}") },
            )
            v.getOdometer().registerDistanceDriven(object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(value: Float?) = log("Odometer 변경(km): $value")
                override fun onFailed(e: Exception) = log("Odometer 구독 실패: ${e.message}")
            })
        }.onFailure { log("Vehicle 예외: $it") }
    }

    private fun probeLocation() {
        val fl = fusedLocation ?: FusedLocation(this).also {
            it.initialize()
            fusedLocation = it
        }
        runCatching {
            log("FusedLocation isSupported=${fl.isSupported()}")
            val listener = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) =
                    log("위치: ${location?.latitude}, ${location?.longitude} conf=${location?.confidence}")
                override fun onError(e: Exception) = log("위치 실패: ${e.javaClass.simpleName} ${e.message}")
            }
            fl.getLastKnownLocation(listener)
            fl.registerFusedLocationCallback(listener)
        }.onFailure { log("FusedLocation 예외: $it") }
    }

    private fun probeTts() {
        val existing = tts
        if (existing != null) {
            existing.speak("회사 도착, 12킬로미터. 출근으로 기록할까요?", true)
            return
        }
        val t = TextToSpeech(this, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
        tts = t
        t.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                log("TTS 초기화 성공")
                // initialize 성공 전에 addEventListener를 부르면 SDK 내부 lateinit 예외로 앱이 죽는다(3.3.1 확인).
                t.addEventListener(object : EventListener {
                    override fun onReady() = log("TTS onReady")
                    override fun onStart() = log("TTS onStart")
                    override fun onDone() = log("TTS onDone")
                    override fun onStop() = log("TTS onStop")
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) = log("TTS onError $errorType")
                })
                t.setLocale(java.util.Locale.KOREA)
                t.speak("회사 도착, 12킬로미터. 출근으로 기록할까요?", true)
            }
            override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) = log("TTS 초기화 실패 $errorType")
        })
    }

    private fun probeStt() {
        val existing = stt
        if (existing != null) {
            existing.request()
            log("STT request()")
            return
        }
        val s = SpeechToText(this, ai.pleos.playground.stt.constant.Mode.ON_DEVICE)
        stt = s
        s.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
            override fun onSuccess() {
                log("STT 초기화 성공")
                s.setLocale(java.util.Locale.KOREA)
                s.addListener(object : ResultListener {
                    override fun onReady() = log("STT onReady")
                    override fun onStartedRecognition() = log("STT onStartedRecognition")
                    override fun onUpdated(stt: String, completed: Boolean) = log("STT 결과: '$stt' completed=$completed")
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onEndedRecognition() = log("STT onEndedRecognition")
                    override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) = log("STT onError $errorType")
                })
                s.request()
                log("STT request()")
            }
            override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) = log("STT 초기화 실패 $errorType")
        })
    }

    /** 앱 전용 폴더의 probe.pcm(16kHz·16bit·mono)을 마이크 대신 STT에 넣는다. ON_DEVICE 전용 기능. */
    private fun probeSttFile() {
        val s = stt ?: run {
            log("먼저 stt를 실행해 초기화하세요")
            return
        }
        val file = java.io.File(filesDir, "probe.pcm")
        if (!file.exists()) {
            log("파일 없음: ${file.absolutePath}")
            return
        }
        thread {
            val pcm = file.readBytes()
            log("STT 파일 전송 ${pcm.size}바이트")
            pcm.asList().chunked(3_200).forEach { chunk ->
                s.sendAudio(chunk.toByteArray())
                Thread.sleep(100L)
            }
            s.completeAudioSend()
            log("STT 파일 전송 완료")
        }
    }

    private fun probeNetwork() {
        thread {
            listOf(
                "http://10.0.2.2:8080/actuator/health",
                "https://autolog-api.aihub.io.kr/actuator/health",
            ).forEach { url ->
                val result = runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    try {
                        "HTTP ${conn.responseCode}"
                    } finally {
                        conn.disconnect()
                    }
                }.getOrElse { "실패 ${it.javaClass.simpleName}: ${it.message}" }
                log("네트워크 $url → $result")
            }
        }
    }

    /** 버튼 3개짜리 heads-up 알림이 Pleos 화면 위로 뜨는지 확인한다(도착 질문 알림 검토용). */
    private fun probeNotify() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel("probe_arrival", "도착 확인(점검)", android.app.NotificationManager.IMPORTANCE_HIGH),
        )
        fun action(label: String, code: Int) = android.app.Notification.Action.Builder(
            null, label,
            android.app.PendingIntent.getActivity(
                this, code, android.content.Intent(this, ProbeActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
        val notification = android.app.Notification.Builder(this, "probe_arrival")
            .setSmallIcon(com.autolog.app.R.drawable.ic_notification)
            .setContentTitle("본사 도착, 12.4킬로미터")
            .setContentText("출근으로 기록할까요?")
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .addAction(action("출퇴근", 1))
            .addAction(action("업무", 2))
            .addAction(action("개인", 3))
            .build()
        manager.notify(9001, notification)
        log("알림 게시: 채널 중요도 HIGH, 버튼 3개")
    }

    /** Android Geocoder로 좌표 → 동 이름을 얻을 수 있는지 확인한다(미등록 장소 표시 검토용). */
    private fun probeGeocoder() {
        log("Geocoder.isPresent=${android.location.Geocoder.isPresent()}")
        thread {
            val result = runCatching {
                @Suppress("DEPRECATION")
                android.location.Geocoder(this, java.util.Locale.KOREA).getFromLocation(37.4842, 126.9297, 1)
                    ?.firstOrNull()?.let { "${it.adminArea} ${it.locality} ${it.subLocality} ${it.thoroughfare} / ${it.getAddressLine(0)}" }
            }.getOrElse { "실패 ${it.javaClass.simpleName}: ${it.message}" }
            log("Geocoder 결과: $result")
        }
    }

    /** NaviHelper로 현재 위치 주소·목적지를 받아 오는지 확인한다(출발·도착 동 이름 표시 검토용). */
    private fun probeNavi() {
        val helper = navi
        if (helper != null && naviConnected) {
            requestNaviInfo(helper)
            return
        }
        val created = helper ?: NaviHelper(applicationContext).also { navi = it }
        created.initialize(
            onConnected = {
                naviConnected = true
                log("NaviHelper 연결됨")
                created.addListener(object : NaviHelperEventListener {
                    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
                        log("현재 위치 주소: '${currentLocationInfo.address}' (${currentLocationInfo.latitude}, ${currentLocationInfo.longitude})")

                    override fun onDestinationInfo(destinationInfo: DestinationInfo) =
                        log("목적지: '${destinationInfo.name}' / '${destinationInfo.address}'")

                    override fun onError(errorCode: NaviErrorCode) = log("NaviHelper 오류: $errorCode")
                })
                requestNaviInfo(created)
            },
            onFailed = { log("NaviHelper 연결 실패: ${it.javaClass.simpleName} ${it.message}") },
        )
    }

    private fun requestNaviInfo(helper: NaviHelper) {
        log("현재 위치·목적지 요청")
        runCatching {
            helper.getCurrentLocationInfo()
            helper.getDestinationInfo()
        }.onFailure { log("요청 실패: ${it.javaClass.simpleName} ${it.message}") }
    }

    override fun onDestroy() {
        navi?.release()
        tts?.release()
        stt?.release()
        fusedLocation?.release()
        vehicle?.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "AutoLogProbe"
        const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
