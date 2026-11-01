package com.autolog.app.pairing

import com.autolog.app.data.remote.ApiFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingControllerTest {
    private lateinit var server: MockWebServer
    private val settings = object : PairingSettings {
        var pairingToken: String? = null
        var promoted = false
        override suspend fun installationId() = "install-1"
        override suspend fun savePairingToken(token: String) {
            pairingToken = token
        }
        override suspend fun promotePairingToken() {
            promoted = true
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    private fun code(id: String, code: String) =
        json(201, """{"success":true,"data":{"pairingId":"$id","code":"$code","expiresAt":"2026-11-03T08:22:00+09:00"},"error":null}""")
    private fun status(status: String, vehicle: String = "null") =
        json(200, """{"success":true,"data":{"status":"$status","expiresAt":"2026-11-03T08:22:00+09:00","vehicle":$vehicle},"error":null}""")

    private fun controller(scope: kotlinx.coroutines.CoroutineScope, onPaired: suspend () -> Unit = {}) = PairingController(
        api = ApiFactory.create(server.url("/api/v1/").toString()) { null },
        settings = settings, scope = scope, onPaired = onPaired, pollIntervalMs = 10, newToken = { "token-abc" },
    )

    @Test
    fun `코드를 받아 표시하고 PAIRED면 토큰을 올리고 설정을 받는다`() = runBlocking {
        server.enqueue(code("p1", "482913"))
        server.enqueue(status("PENDING"))
        server.enqueue(status("PAIRED", """{"regNo":"12가3456","modelName":"아이오닉 5"}"""))
        var configLoaded = false
        val c = controller(this) { configLoaded = true }

        c.start()
        val shown = withTimeout(5_000) { c.state.first { it is PairingController.State.ShowingCode } }
        assertEquals("482913", (shown as PairingController.State.ShowingCode).code)
        val paired = withTimeout(5_000) { c.state.first { it is PairingController.State.Paired } }

        assertEquals("12가3456", (paired as PairingController.State.Paired).regNo)
        assertTrue(settings.promoted)
        assertTrue(configLoaded)
        assertEquals("token-abc", settings.pairingToken)
        val issue = server.takeRequest()
        assertEquals("/api/v1/devices/pairing-codes", issue.path)
        assertTrue(issue.body.readUtf8().contains("\"tokenHash\":\"${DeviceTokens.sha256Hex("token-abc")}\""))
        assertEquals("/api/v1/devices/pairing-codes/p1", server.takeRequest().path)
    }

    @Test
    fun `만료되면 새 코드를 받는다`() = runBlocking {
        server.enqueue(code("p1", "111111"))
        server.enqueue(status("EXPIRED"))
        server.enqueue(code("p2", "222222"))
        server.enqueue(status("PAIRED"))
        val c = controller(this)

        c.start()
        withTimeout(5_000) { c.state.first { it is PairingController.State.Paired } }
        server.takeRequest()
        server.takeRequest()
        assertEquals("/api/v1/devices/pairing-codes", server.takeRequest().path)
        assertEquals("/api/v1/devices/pairing-codes/p2", server.takeRequest().path)
    }

    @Test
    fun `코드 발급이 거절되면 오류를 보여준다`() = runBlocking {
        server.enqueue(json(429, """{"success":false,"data":null,"error":{"code":"RATE_LIMITED","message":"요청이 너무 많습니다."}}"""))
        val c = controller(this)
        c.start()
        val error = withTimeout(5_000) { c.state.first { it is PairingController.State.Error } }
        assertEquals("요청이 너무 많습니다.", (error as PairingController.State.Error).message)
    }
}
