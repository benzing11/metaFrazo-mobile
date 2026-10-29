package com.metafrazo.app.core.meeting

object MeetingCode {
    const val LENGTH = 6

    /**
     * Accepts a raw code ("ab3 xyz"), an https join link (https://metafrazo.app/join/AB3XYZ),
     * or a custom-scheme link (metafrazo://join/AB3XYZ) and returns the normalized code.
     */
    fun extract(input: String): String {
        val trimmed = input.trim()
        val candidate = if (trimmed.contains("/")) {
            trimmed.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        } else {
            trimmed
        }
        return candidate.uppercase().filter { it.isLetterOrDigit() }
    }

    fun isValid(code: String): Boolean = code.length == LENGTH

    fun joinLink(code: String): String = "https://metafrazo.app/join/$code"
}
