package com.metafrazo.app.core.meeting

import java.net.URI

/** Resolves development LiveKit loopback URLs so they also work on emulators and physical phones. */
object MediaServerUrl {
    fun resolve(advertisedUrl: String, backendUrl: String): String {
        return runCatching {
            val media = URI(advertisedUrl)
            val backend = URI(backendUrl)
            val mediaHost = media.host ?: return advertisedUrl
            val backendHost = backend.host ?: return advertisedUrl
            if (!mediaHost.isLoopback() || backendHost == "127.0.0.1") return advertisedUrl

            URI(
                media.scheme,
                media.userInfo,
                backendHost,
                media.port,
                media.path,
                media.query,
                media.fragment,
            ).toString()
        }.getOrDefault(advertisedUrl)
    }

    private fun String.isLoopback(): Boolean =
        this == "127.0.0.1" || this.equals("localhost", ignoreCase = true) || this == "::1"
}
