package com.metafrazo.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiUrlTest {
    @Test
    fun normalize_trimsWhitespaceAndTrailingSlash() {
        assertEquals("http://127.0.0.1:3000", Api.normalizeUrl("  http://127.0.0.1:3000/ "))
    }

    @Test
    fun valid_usbLoopback() {
        assertTrue(Api.isValidUrl("http://127.0.0.1:3000"))
    }

    @Test
    fun valid_lanHttp() {
        assertTrue(Api.isValidUrl("http://192.168.1.42:3000"))
    }

    @Test
    fun valid_https() {
        assertTrue(Api.isValidUrl("https://api.metafrazo.app"))
    }

    @Test
    fun invalid_blank() {
        assertFalse(Api.isValidUrl("   "))
    }

    @Test
    fun invalid_scheme() {
        assertFalse(Api.isValidUrl("ws://127.0.0.1:7880"))
    }

    @Test
    fun invalid_noHost() {
        assertFalse(Api.isValidUrl("http://"))
    }
}
