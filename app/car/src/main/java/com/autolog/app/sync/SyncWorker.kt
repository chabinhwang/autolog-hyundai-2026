package com.autolog.app.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autolog.app.AutologApp
import java.util.concurrent.TimeUnit

/** WorkManager 작업 `trip-sync` (rules §6). 네트워크 연결 조건, 지수 백오프. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AutologApp).container
        return when (val outcome = container.syncEngine.run()) {
            SyncEngine.Outcome.SUCCESS -> Result.success()
            SyncEngine.Outcome.RETRY -> Result.retry()
            SyncEngine.Outcome.UNPAIRED -> {
                Log.i(TAG, "동기화 중단: $outcome")
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = "AutoLogSync"
        private const val UNIQUE_NOW = "trip-sync"
        private const val UNIQUE_PERIODIC = "trip-sync-periodic"

        private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 트립·수정 저장 직후, 앱 시작 시 호출. 이미 대기 중이면 하나로 합친다. */
        fun requestNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** 15분 주기(WorkManager 최소값). 관리자가 바꾼 장소·직원을 내려받는다. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
