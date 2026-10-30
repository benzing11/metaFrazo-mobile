package com.metafrazo.app.core.network

import android.content.Context
import com.metafrazo.app.BuildConfig
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Api {
    @Volatile var backendUrl: String = BuildConfig.BACKEND_URL.trimEnd('/')
    @Volatile var aiUrl: String = BuildConfig.AI_SERVICE_URL.trimEnd('/')
    private const val PREFS = "metafrazo_net"

    fun configure(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        backendUrl = p.getString("backend_url", BuildConfig.BACKEND_URL)?.trimEnd('/') ?: BuildConfig.BACKEND_URL.trimEnd('/')
        aiUrl = p.getString("ai_url", BuildConfig.AI_SERVICE_URL)?.trimEnd('/') ?: BuildConfig.AI_SERVICE_URL.trimEnd('/')
    }

    fun setUrls(ctx: Context, backend: String, ai: String) {
        val b = normalizeUrl(backend)
        val a = normalizeUrl(ai)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("backend_url", b)
            .putString("ai_url", a)
            .apply()
        backendUrl = b
        aiUrl = a
    }

    const val DEFAULT_USB_BACKEND = "http://127.0.0.1:3000"
    const val DEFAULT_USB_AI = "http://127.0.0.1:8000"

    /** Trims and strips a trailing slash. Does not validate. */
    fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/')

    /** True when [raw] is a non-blank http(s) URL with a host. */
    fun isValidUrl(raw: String): Boolean {
        val u = normalizeUrl(raw)
        if (u.isEmpty()) return false
        return try {
            val parsed = URL(u)
            (parsed.protocol == "http" || parsed.protocol == "https") && !parsed.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    @Volatile var authToken: String? = null
    @Volatile var devUserId: String = "dev-user"
    @Volatile var devUserName: String = ""

    /** Auth headers for backend calls; also used by media/data-source factories (e.g. ExoPlayer). */
    fun authHeaders(): Map<String, String> {
        val token = authToken
        if (!token.isNullOrBlank()) return mapOf("Authorization" to "Bearer $token")
        // Header values must be plain ASCII.
        val name = devUserName.filter { it in ' '..'~' }.trim()
        return buildMap {
            put("X-Dev-User-Id", devUserId)
            if (name.isNotEmpty()) put("X-Dev-User-Name", name)
        }
    }

    /** Never throws: network failures return code -1 with a readable message so callers can show an error state. */
    fun request(method: String, path: String, body: String? = null): Pair<Int, String> = try {
        doRequest(method, path, body)
    } catch (e: Exception) {
        -1 to reachabilityError(e)
    }

    /**
     * Turns a raw network exception into an actionable message. The common dev failure is a
     * loopback backend URL with no bridge in place: on a physical phone 127.0.0.1/localhost is the
     * phone itself, so the connection is dropped ("unexpected end of stream") or refused.
     */
    private fun reachabilityError(e: Exception): String {
        val detail = "${e.javaClass.simpleName}: ${e.message}"
        val host = runCatching { URL(backendUrl).host?.lowercase() }.getOrNull()
        val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
        if (isLoopback) {
            return "Can't reach the dev backend at $backendUrl. On USB, run " +
                "`adb reverse tcp:3000 tcp:3000` (also 8000, 7880, 7881). " +
                "On Wi-Fi, set your computer's LAN URL (e.g. http://192.168.x.x:3000) in Settings. [$detail]"
        }
        return "Can't reach server at $backendUrl ($detail)"
    }

    private fun doRequest(method: String, path: String, body: String?): Pair<Int, String> {
        val conn = URL(backendUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Content-Type", "application/json")
        authHeaders().forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        return code to text
    }

    fun post(path: String, json: String): Pair<Int, String> = request("POST", path, json)
    fun get(path: String): Pair<Int, String> = request("GET", path)

    /**
     * Uploads a single binary part plus string form fields as multipart/form-data.
     * Used for server-side screen OCR (sending a JPEG frame to /api/screen/analyze).
     * Never throws: failures return code -1 with a readable message.
     */
    fun postMultipart(
        path: String,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileContentType: String,
        fields: Map<String, String> = emptyMap(),
    ): Pair<Int, String> = try {
        val boundary = "----metafrazo${System.nanoTime()}"
        val conn = URL(backendUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        authHeaders().forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val crlf = "\r\n"
        val dash = "--"
        conn.outputStream.use { out ->
            for ((k, v) in fields) {
                out.write(("$dash$boundary$crlf").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"$k\"$crlf$crlf").toByteArray())
                out.write(v.toByteArray())
                out.write(crlf.toByteArray())
            }
            out.write(("$dash$boundary$crlf").toByteArray())
            out.write(("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"$crlf").toByteArray())
            out.write(("Content-Type: $fileContentType$crlf$crlf").toByteArray())
            out.write(fileBytes)
            out.write(crlf.toByteArray())
            out.write(("$dash$boundary$dash$crlf").toByteArray())
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        code to text
    } catch (e: Exception) {
        -1 to reachabilityError(e)
    }

    /** Quick health probe against the current backend. Returns true on HTTP 2xx. */
    fun checkBackend(): Boolean = try {
        val (code, _) = doRequest("GET", "/api/health", null)
        code in 200..299
    } catch (_: Exception) {
        false
    }
}