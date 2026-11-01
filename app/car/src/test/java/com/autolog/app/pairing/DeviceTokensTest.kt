package com.autolog.app.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTokensTest {
    @Test
    fun `토큰은 32바이트 base64url이고 점이 없다`() {
        val token = DeviceTokens.generate()
        assertEquals(43, token.length)
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(token.contains('.'))
        assertNotEquals(token, DeviceTokens.generate())
    }

    @Test
    fun `해시는 서버와 같은 SHA-256 소문자 hex`() {
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            DeviceTokens.sha256Hex("test"),
        )
    }
}
