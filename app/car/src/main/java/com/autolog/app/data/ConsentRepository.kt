package com.autolog.app.data

import com.autolog.app.data.local.ConsentDao
import com.autolog.app.data.local.ConsentEntity
import com.autolog.app.data.settings.SettingsStore
import java.time.Clock

/** 직원 개인정보 동의 (rules §11). 이용자로 처음 선택될 때 받는다. */
class ConsentRepository(
    private val consentDao: ConsentDao,
    private val settings: SettingsStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** 현재 동의 문서 버전에 아직 동의하지 않았으면 true. 버전을 모르면(설정 미수신) 동의를 받는다. */
    suspend fun needsConsent(driverId: Long): Boolean {
        val version = settings.currentConsentVersion() ?: return true
        return !consentDao.exists(driverId, version)
    }

    /** @return 동의 문서 버전을 아직 모르면(설정 미수신) false */
    suspend fun recordConsent(driverId: Long): Boolean {
        val version = settings.currentConsentVersion() ?: return false
        consentDao.insert(ConsentEntity(driverId, version, clock.instant(), synced = false))
        return true
    }
}
