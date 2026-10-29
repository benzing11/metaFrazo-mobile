package com.metafrazo.app.core.meeting

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaServerUrlTest {
    @Test fun rewritesLoopbackForPhysicalPhone() {
        assertEquals(
            "ws://192.168.0.142:7880",
            MediaServerUrl.resolve("ws://127.0.0.1:7880", "http://192.168.0.142:3000"),
        )
    }

    @Test fun rewritesLoopbackForEmulator() {
        assertEquals(
            "ws://10.0.2.2:7880",
            MediaServerUrl.resolve("ws://localhost:7880", "http://10.0.2.2:3000"),
        )
    }

    @Test fun preservesAdbReverseLoopback() {
        assertEquals(
            "ws://127.0.0.1:7880",
            MediaServerUrl.resolve("ws://127.0.0.1:7880", "http://127.0.0.1:3000"),
        )
    }

    @Test fun preservesPublicMediaServer() {
        assertEquals(
            "wss://live.example.com/rtc?token=x",
            MediaServerUrl.resolve("wss://live.example.com/rtc?token=x", "https://api.example.com"),
        )
    }

    @Test fun malformedUrlFallsBackSafely() {
        assertEquals("not a url", MediaServerUrl.resolve("not a url", "http://192.168.0.142:3000"))
    }
}
