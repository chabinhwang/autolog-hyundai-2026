package com.autolog.app.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.autolog.app.AutologApp
import com.autolog.app.BuildConfig
import com.autolog.app.MainActivity
import com.autolog.app.R
import com.autolog.app.location.PleosLocationSource
import com.autolog.app.sync.SyncWorker
import com.autolog.app.vehicle.PleosVehicleSource
import com.autolog.app.voice.PleosVoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration

/**
 * 트립 감지 Foreground Service. 화면 조작 없이 기어·Odometer를 계속 듣는다.
 * 도착 확인 질문(TTS/STT) 때 마이크를 쓰므로 가능하면 microphone 유형도 함께 건다.
 */
class TripRecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vehicle: PleosVehicleSource? = null
    private var location: PleosLocationSource? = null
    private var voice: PleosVoiceIO? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground(getString(R.string.recorder_idle))
        val container = (application as AutologApp).container
        val vehicleSource = PleosVehicleSource(this).also { vehicle = it }
        val locationSource = PleosLocationSource(this).also { location = it }
        val voiceIO = PleosVoiceIO(this, debugAudio = if (BuildConfig.DEBUG) ::takeDebugAnswer else null).also { voice = it }
        val recorder = TripRecorder(
            vehicle = vehicleSource,
            location = locationSource,
            voice = voiceIO,
            repository = container.tripRepository,
            settings = container.settings,
            requestSync = { SyncWorker.requestNow(this) },
            parkConfirm = Duration.ofSeconds(BuildConfig.PARK_CONFIRM_SECONDS.toLong()),
        )
        recorder.start(scope)
        scope.launch {
            recorder.status.collect { status ->
                container.recorderStatus.value = status
                val text = if (status.driving) getString(R.string.recorder_driving) else getString(R.string.recorder_idle)
                notificationManager().notify(NOTIFICATION_ID, notification(text))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        voice?.release()
        location?.release()
        vehicle?.release()
        super.onDestroy()
    }

    /**
     * debug 빌드 전용: filesDir/voice_answer.pcm이 있으면 마이크 대신 그 음성을 한 번 넣고 지운다.
     * 마이크를 쓸 수 없는 개발 PC에서 도착 확인 흐름을 자동으로 시연·검증하기 위한 것.
     */
    private fun takeDebugAnswer(): ByteArray? {
        val file = File(filesDir, "voice_answer.pcm")
        if (!file.exists()) return null
        return file.readBytes().also { file.delete() }
    }

    private fun startAsForeground(text: String) {
        val withMic = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            startForeground(NOTIFICATION_ID, notification(text), withMic)
        } catch (e: SecurityException) {
            // 백그라운드(부팅 직후 등)에서 시작되면 microphone 유형은 거부된다.
            Log.w(TAG, "microphone 유형 없이 시작: ${e.message}")
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun notification(text: String): Notification {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.recorder_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AutoLogService"
        private const val CHANNEL_ID = "trip_recorder"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TripRecorderService::class.java))
        }
    }
}

/** 부팅 후 페어링된 차량이면 기록 서비스를 시작한다. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as AutologApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (container.settings.deviceToken() != null) TripRecorderService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
