package com.autolog.app.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autolog.app.AutologApp
import com.autolog.app.MainActivity
import com.autolog.app.R
import com.autolog.app.domain.Purpose
import com.autolog.app.purpose.ArrivalChoice
import com.autolog.app.sync.SyncWorker
import kotlinx.coroutines.launch

/**
 * 도착 질문을 알림 센터에도 남긴다(출퇴근·업무·개인 버튼). 앱을 열지 않아도 나중에 알림에서 고를 수 있다.
 * Pleos는 시스템 앱 알림만 화면 위로 띄우므로(heads-up) 일반 앱 알림은 알림 센터에만 보인다 (AVD 로그 확인 2026-11-03).
 */
interface ArrivalNotifications {
    /** 질문 알림. [prompt]는 "○○ 도착, 12.4킬로미터. 출근으로 기록할까요?" 형태. */
    fun ask(tripUuid: String, prompt: String)

    /** 용도가 정해지면 안내로 바꾸고 잠시 뒤 지운다. */
    fun confirmed(tripUuid: String, message: String)

    fun cancel(tripUuid: String)

    companion object {
        val None = object : ArrivalNotifications {
            override fun ask(tripUuid: String, prompt: String) = Unit
            override fun confirmed(tripUuid: String, message: String) = Unit
            override fun cancel(tripUuid: String) = Unit
        }
    }
}

class AndroidArrivalNotifications(private val context: Context) : ArrivalNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.arrival_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    override fun ask(tripUuid: String, prompt: String) {
        ensureChannel()
        // "○○ 도착, 12.4킬로미터." / "출근으로 기록할까요?"로 나눠 제목·본문에 쓴다.
        val split = prompt.indexOf(". ")
        val title = if (split > 0) prompt.substring(0, split) else prompt
        val body = if (split > 0) prompt.substring(split + 2) else "운행 용도를 골라 주세요."
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp())
            .addAction(action(tripUuid, ArrivalChoice.COMMUTE, "출퇴근"))
            .addAction(action(tripUuid, ArrivalChoice.BUSINESS, "업무"))
            .addAction(action(tripUuid, ArrivalChoice.PERSONAL, "개인"))
            .build()
        manager.notify(idOf(tripUuid), notification)
    }

    override fun confirmed(tripUuid: String, message: String) {
        ensureChannel()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(message)
            .setContentIntent(openApp())
            .setTimeoutAfter(CONFIRMED_VISIBLE_MS)
            .setAutoCancel(true)
            .build()
        manager.notify(idOf(tripUuid), notification)
    }

    override fun cancel(tripUuid: String) {
        manager.cancel(idOf(tripUuid))
    }

    private fun openApp() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(tripUuid: String, choice: ArrivalChoice, label: String): Notification.Action {
        val intent = Intent(context, ArrivalActionReceiver::class.java)
            .putExtra(EXTRA_TRIP, tripUuid)
            .putExtra(EXTRA_CHOICE, choice.name)
        val pending = PendingIntent.getBroadcast(
            context, (tripUuid + choice.name).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, pending).build()
    }

    companion object {
        private const val CHANNEL_ID = "arrival"
        private const val CONFIRMED_VISIBLE_MS = 4_000L
        const val EXTRA_TRIP = "trip_uuid"
        const val EXTRA_CHOICE = "choice"

        fun idOf(tripUuid: String) = 2_000 + (tripUuid.hashCode() and 0x0FFFFFFF)
    }
}

/**
 * 알림 버튼. 그 운행을 지금 묻는 중이면 팝업 버튼과 같은 경로로 넘기고,
 * 질문이 끝난 뒤(미확인으로 남은 운행)면 화면 목록에서 고른 것과 같이 바로 반영한다.
 */
class ArrivalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tripUuid = intent.getStringExtra(AndroidArrivalNotifications.EXTRA_TRIP) ?: return
        val choice = intent.getStringExtra(AndroidArrivalNotifications.EXTRA_CHOICE)
            ?.let { runCatching { ArrivalChoice.valueOf(it) }.getOrNull() } ?: return
        val container = (context.applicationContext as AutologApp).container
        val status = container.recorderStatus.value
        if (status.phase == TripRecorder.Phase.ASKING && status.tripUuid == tripUuid) {
            container.arrivalTaps.send(choice)
            return
        }
        val purpose = when (choice) {
            ArrivalChoice.COMMUTE -> Purpose.COMMUTE
            ArrivalChoice.BUSINESS -> Purpose.BUSINESS
            ArrivalChoice.PERSONAL -> Purpose.PERSONAL
            ArrivalChoice.LATER -> return
        }
        val pending = goAsync()
        container.appScope.launch {
            try {
                val trip = container.database.tripDao().find(tripUuid)
                if (trip != null && trip.purpose == Purpose.UNCONFIRMED && !trip.answerPending) {
                    container.tripRepository.changePurpose(tripUuid, purpose, null)
                    SyncWorker.requestNow(context)
                    container.arrivalNotifications.confirmed(
                        tripUuid,
                        TripRecorder.confirmation(com.autolog.app.purpose.PurposeDecision(purpose, com.autolog.app.domain.PurposeSource.MANUAL, null)),
                    )
                } else {
                    container.arrivalNotifications.cancel(tripUuid)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
