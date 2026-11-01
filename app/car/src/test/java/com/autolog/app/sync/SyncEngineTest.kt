package com.autolog.app.sync

import com.autolog.app.data.local.ConsentEntity
import com.autolog.app.data.remote.ApiFactory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.domain.SyncState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class SyncEngineTest {
    private lateinit var server: MockWebServer
    private val trips = FakeTripDao()
    private val edits = FakeEditDao()
    private val places = FakePlaceDao()
    private val drivers = FakeDriverDao()
    private val consents = FakeConsentDao()
    private val settings = FakeSettings()
    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)

    private val configBody = """
        {"success":true,"data":{"serverTime":"2026-11-03T09:00:00+09:00","consentVersion":"2026-11-01",
         "vehicle":{"id":3,"regNo":"12가3456","modelName":"아이오닉 5","defaultDriverId":11},
         "drivers":[{"id":11,"dept":"영업1팀","name":"김영업","active":true}],
         "places":[{"id":1,"name":"본사","type":"COMPANY","lat":37.49,"lng":127.02,"radiusM":200,"deleted":false}]},
         "error":null}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun engine() = SyncEngine(
        api = ApiFactory.create(server.url("/api/v1/").toString()) { runBlocking { settings.deviceToken() } },
        settings = settings, tripDao = trips, editDao = edits, placeDao = places, driverDao = drivers,
        consentDao = consents, appVersion = "0.1.0", clock = clock,
    )

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun uploadResults(vararg results: Triple<String, String, String?>) = json(
        200,
        """{"success":true,"data":{"results":[${results.joinToString(",") { (uuid, result, code) ->
            """{"clientUuid":"$uuid","result":"$result","errorCode":${code?.let { "\"$it\"" } ?: "null"}}"""
        }}]},"error":null}""",
    )

    @Test
    fun `토큰이 없으면 요청하지 않고 UNPAIRED`() = runBlocking {
        settings.token = null
        assertEquals(SyncEngine.Outcome.UNPAIRED, engine().run())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `트립 업로드 건별 결과를 반영하고 설정을 내려받는다`() = runBlocking {
        trips.insert(Fixtures.trip("a", Instant.parse("2026-11-02T23:00:00Z")))
        trips.insert(Fixtures.trip("b", Instant.parse("2026-11-02T23:10:00Z")))
        trips.insert(Fixtures.trip("c", Instant.parse("2026-11-02T23:20:00Z")))
        server.enqueue(uploadResults(Triple("a", "CREATED", null), Triple("b", "DUPLICATE", null), Triple("c", "REJECTED", "VEHICLE_MISMATCH")))
        server.enqueue(json(200, configBody))

        assertEquals(SyncEngine.Outcome.SUCCESS, engine().run())

        val upload = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("PUT", upload.method)
        assertEquals("/api/v1/trips/batch", upload.path)
        assertEquals("Bearer device-token", upload.getHeader("Authorization"))
        val body = upload.body.readUtf8()
        assertTrue(body, body.contains("\"appVersion\":\"0.1.0\""))
        assertTrue(body, body.contains("\"startedAt\":\"2026-11-03T08:00:00+09:00\""))

        assertEquals(SyncState.SYNCED, trips.find("a")!!.syncState)
        assertEquals(SyncState.SYNCED, trips.find("b")!!.syncState)
        assertEquals(SyncState.FAILED, trips.find("c")!!.syncState)
        assertEquals("VEHICLE_MISMATCH", trips.find("c")!!.syncErrorCode)

        val config = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/devices/me/config", config.path)
        assertEquals("2026-11-03T09:00:00+09:00", settings.since)
        assertEquals("12가3456", settings.vehicle!!.regNo)
        assertEquals("김영업", drivers.find(11)!!.name)
        assertEquals(EndpointType.COMPANY, places.findByServerId(1)!!.type)
    }

    @Test
    fun `다음 설정 요청은 since를 보낸다`() = runBlocking {
        settings.since = "2026-11-03T09:00:00+09:00"
        server.enqueue(json(200, configBody))
        engine().run()
        assertEquals("/api/v1/devices/me/config?since=2026-11-03T09%3A00%3A00%2B09%3A00", server.takeRequest().path)
    }

    @Test
    fun `음성 확인을 기다리는 트립은 올리지 않는다`() = runBlocking {
        trips.insert(Fixtures.trip("a").copy(answerPending = true))
        server.enqueue(json(200, configBody))
        engine().run()
        assertEquals("/api/v1/devices/me/config", server.takeRequest().path)
        assertEquals(SyncState.PENDING, trips.find("a")!!.syncState)
    }

    @Test
    fun `401이면 토큰을 지우고 기록은 그대로 둔다`() = runBlocking {
        trips.insert(Fixtures.trip("a"))
        server.enqueue(json(401, """{"success":false,"data":null,"error":{"code":"DEVICE_TOKEN_INVALID","message":"x"}}"""))

        assertEquals(SyncEngine.Outcome.UNPAIRED, engine().run())
        assertNull(settings.token)
        assertEquals(SyncState.PENDING, trips.find("a")!!.syncState)
    }

    @Test
    fun `서버 오류나 네트워크 실패는 재시도`() = runBlocking {
        trips.insert(Fixtures.trip("a"))
        server.enqueue(json(503, "{}"))
        assertEquals(SyncEngine.Outcome.RETRY, engine().run())
        assertEquals(SyncState.PENDING, trips.find("a")!!.syncState)

        server.shutdown()
        assertEquals(SyncEngine.Outcome.RETRY, engine().run())
    }

    @Test
    fun `업로드 중 로컬 용도가 바뀌면 수정 이력으로 다시 보낸다`() = runBlocking {
        trips.insert(Fixtures.trip("a").copy(purpose = Purpose.UNCONFIRMED, purposeSource = PurposeSource.AUTO))
        trips.beforeMarkSynced = { trips.updatePurpose("a", Purpose.PERSONAL, PurposeSource.MANUAL, null) }
        server.enqueue(uploadResults(Triple("a", "CREATED", null)))
        server.enqueue(json(200, """{"success":true,"data":{},"error":null}"""))
        server.enqueue(json(200, configBody))

        assertEquals(SyncEngine.Outcome.SUCCESS, engine().run())

        server.takeRequest()
        val edit = server.takeRequest()
        assertEquals("POST", edit.method)
        assertEquals("/api/v1/trips/a/edits", edit.path)
        val body = edit.body.readUtf8()
        assertTrue(body, body.contains("\"purpose\":\"PERSONAL\""))
        assertTrue(body, body.contains("\"purposeSource\":\"MANUAL\""))
        assertEquals(SyncState.SYNCED, trips.find("a")!!.syncState)
        assertEquals(SyncState.SYNCED, edits.rows.values.single().syncState)
    }

    @Test
    fun `트립이 아직 없다는 수정은 다음에 다시 보낸다`() = runBlocking {
        edits.insert(
            com.autolog.app.data.local.TripEditEntity(
                "e1", "a", 3, clock.instant(), PurposeSource.MANUAL, Purpose.BUSINESS, null, SyncState.PENDING, null,
            ),
        )
        server.enqueue(json(404, """{"success":false,"data":null,"error":{"code":"TRIP_NOT_FOUND","message":"x"}}"""))
        server.enqueue(json(200, configBody))
        engine().run()
        assertEquals(SyncState.PENDING, edits.rows.getValue("e1").syncState)
    }

    @Test
    fun `동의 기록을 먼저 보낸다`() = runBlocking {
        consents.insert(ConsentEntity(11, "2026-11-01", Instant.parse("2026-11-02T23:10:00Z"), synced = false))
        server.enqueue(json(201, """{"success":true,"data":{"driverId":11},"error":null}"""))
        server.enqueue(json(200, configBody))
        engine().run()
        val request = server.takeRequest()
        assertEquals("/api/v1/devices/me/consents", request.path)
        assertTrue(request.body.readUtf8().contains("\"consentedAt\":\"2026-11-03T08:10:00+09:00\""))
        assertTrue(consents.rows.single().synced)
    }
}
