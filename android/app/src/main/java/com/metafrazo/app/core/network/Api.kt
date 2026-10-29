package com.metafrazo.app.core.network

import com.metafrazo.app.BuildConfig
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Api {
    val backendUrl: String = BuildConfig.BACKEND_URL.trimEnd('/')
    val aiUrl: String = BuildConfig.AI_SERVICE_URL.trimEnd('/')

    fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
        val conn = URL(backendUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Dev-User-Id", "dev-user")
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
}