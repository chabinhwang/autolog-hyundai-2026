package com.autolog.app

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun apiBaseUrlIsHttpUrl() {
        assertTrue(BuildConfig.API_BASE_URL.startsWith("http"))
    }
}
