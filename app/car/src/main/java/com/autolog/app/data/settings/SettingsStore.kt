package com.autolog.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autolog.app.pairing.KeystoreCipher
import com.autolog.app.pairing.PairingSettings
import com.autolog.app.sync.SyncSettings
import com.autolog.app.trip.RecorderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autolog_settings")

/** 페어링된 차량 정보 (설정 내려받기 응답). */
@Serializable
data class PairedVehicleInfo(
    val id: Long,
    val regNo: String,
    val modelName: String?,
    val defaultDriverId: Long?,
)

/** 주행 중 상태. 서비스가 다시 시작돼도 트립 시작 값을 잃지 않도록 보관한다. */
@Serializable
data class OngoingTrip(
    val startedAtMillis: Long,
    val odoStartKm: Double,
    val lat: Double?,
    val lng: Double?,
    /** P 진입(종료 후보) 시각·값. 주행 중이면 null. */
    val parkedAtMillis: Long? = null,
    val odoParkedKm: Double? = null,
    val parkedLat: Double? = null,
    val parkedLng: Double? = null,
    /** 운행 시작 버튼으로 시작한 운행. 기어를 무시하고 종료 버튼으로만 끝난다. */
    val manual: Boolean = false,
    /** 수동 운행 중 마지막으로 확인한 시각·계기판 거리·위치(30초마다). 시동이 꺼진 시점을 추정하는 데 쓴다. */
    val lastSeenAtMillis: Long? = null,
    val lastSeenOdoKm: Double? = null,
    val lastSeenLat: Double? = null,
    val lastSeenLng: Double? = null,
    /** 출발지가 등록 장소가 아닐 때 붙일 동 이름(내비 주소). */
    val originArea: String? = null,
)

/**
 * 작은 설정값 저장소(DataStore). 기기 토큰은 Keystore로 암호화해 저장한다(D14).
 */
class SettingsStore(private val context: Context, private val cipher: KeystoreCipher = KeystoreCipher()) : SyncSettings, PairingSettings, RecorderSettings {
    private object Keys {
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
        val DEVICE_TOKEN = stringPreferencesKey("device_token_enc")
        val PAIRING_TOKEN = stringPreferencesKey("pairing_token_enc")
        val VEHICLE = stringPreferencesKey("vehicle_json")
        val CONFIG_SINCE = stringPreferencesKey("config_since")
        val CONSENT_VERSION = stringPreferencesKey("consent_version")
        val CURRENT_DRIVER = longPreferencesKey("current_driver_id")
        val ONGOING_TRIP = stringPreferencesKey("ongoing_trip_json")
        val LAST_REASKED_TRIP = stringPreferencesKey("last_reasked_trip_uuid")
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    val vehicle: Flow<PairedVehicleInfo?> = context.dataStore.data.map { prefs ->
        prefs[Keys.VEHICLE]?.let { runCatching { json.decodeFromString<PairedVehicleInfo>(it) }.getOrNull() }
    }

    val currentDriverId: Flow<Long?> = context.dataStore.data.map { it[Keys.CURRENT_DRIVER] }

    val consentVersion: Flow<String?> = context.dataStore.data.map { it[Keys.CONSENT_VERSION] }

    suspend fun currentConsentVersion(): String? = consentVersion.first()

    /** 페어링 여부. 기기 토큰이 있으면 페어링된 것으로 본다. */
    val paired: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEVICE_TOKEN] != null }

    override suspend fun installationId(): String {
        context.dataStore.data.first()[Keys.INSTALLATION_ID]?.let { return it }
        val created = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.INSTALLATION_ID] = created }
        return created
    }

    /** 페어링 시작: 새 토큰을 암호화해 임시 보관한다(이전 토큰 재사용 안 함). */
    override suspend fun savePairingToken(token: String) {
        val encrypted = cipher.encrypt(token)
        context.dataStore.edit { it[Keys.PAIRING_TOKEN] = encrypted }
    }

    suspend fun pairingToken(): String? =
        context.dataStore.data.first()[Keys.PAIRING_TOKEN]?.let(cipher::decrypt)

    /** PAIRED 확인 후 임시 토큰을 기기 토큰으로 올리고, 이전 차량의 동기화 기준을 지운다. */
    override suspend fun promotePairingToken() {
        context.dataStore.edit { prefs ->
            prefs[Keys.PAIRING_TOKEN]?.let { prefs[Keys.DEVICE_TOKEN] = it }
            prefs.remove(Keys.PAIRING_TOKEN)
            prefs.remove(Keys.CONFIG_SINCE)
            prefs.remove(Keys.VEHICLE)
            prefs.remove(Keys.CURRENT_DRIVER)
        }
    }

    override suspend fun deviceToken(): String? =
        context.dataStore.data.first()[Keys.DEVICE_TOKEN]?.let(cipher::decrypt)

    /** 서버가 토큰을 폐기했을 때(401 DEVICE_TOKEN_INVALID). 로컬 기록은 남긴다. */
    override suspend fun clearDeviceToken() {
        context.dataStore.edit {
            it.remove(Keys.DEVICE_TOKEN)
            it.remove(Keys.CONFIG_SINCE)
        }
    }

    override suspend fun saveVehicle(info: PairedVehicleInfo) {
        context.dataStore.edit { it[Keys.VEHICLE] = json.encodeToString(PairedVehicleInfo.serializer(), info) }
    }

    override suspend fun vehicleNow(): PairedVehicleInfo? = vehicle.first()

    override suspend fun configSince(): String? = context.dataStore.data.first()[Keys.CONFIG_SINCE]

    override suspend fun saveConfigSince(serverTime: String, consentVersion: String) {
        context.dataStore.edit {
            it[Keys.CONFIG_SINCE] = serverTime
            it[Keys.CONSENT_VERSION] = consentVersion
        }
    }

    override suspend fun setCurrentDriver(driverId: Long?) {
        context.dataStore.edit { prefs ->
            if (driverId == null) prefs.remove(Keys.CURRENT_DRIVER) else prefs[Keys.CURRENT_DRIVER] = driverId
        }
    }

    override suspend fun currentDriverNow(): Long? = currentDriverId.first()

    override suspend fun ongoingTrip(): OngoingTrip? =
        context.dataStore.data.first()[Keys.ONGOING_TRIP]?.let { runCatching { json.decodeFromString<OngoingTrip>(it) }.getOrNull() }

    override suspend fun saveOngoingTrip(trip: OngoingTrip?) {
        context.dataStore.edit { prefs ->
            if (trip == null) prefs.remove(Keys.ONGOING_TRIP) else prefs[Keys.ONGOING_TRIP] = json.encodeToString(OngoingTrip.serializer(), trip)
        }
    }

    override suspend fun lastReaskedTrip(): String? = context.dataStore.data.first()[Keys.LAST_REASKED_TRIP]

    override suspend fun saveLastReaskedTrip(uuid: String) {
        context.dataStore.edit { it[Keys.LAST_REASKED_TRIP] = uuid }
    }
}
