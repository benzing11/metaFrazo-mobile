package com.metafrazo.app.ui.screens.diagnostics

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import com.metafrazo.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun DiagnosticsScreen() {
    var backend by remember { mutableStateOf<String?>(null) }
    var ai by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        loading = true
        backend = fetchHealth("${BuildConfig.BACKEND_URL}/api/health")
        ai = fetchHealth("${BuildConfig.AI_SERVICE_URL}/health")
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineLarge)
        Text("Backend: ${BuildConfig.BACKEND_URL}", style = MaterialTheme.typography.bodyMedium)
        if (loading) CircularProgressIndicator() else Text(backend ?: "unknown")
        Text("AI service: ${BuildConfig.AI_SERVICE_URL}", style = MaterialTheme.typography.bodyMedium)
        if (!loading) Text(ai ?: "unknown")
        Button(onClick = { tick++ }) { Text("Refresh") }
    }
}

private suspend fun fetchHealth(url: String): String = withContext(Dispatchers.IO) {
    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText()
        conn.disconnect()
        if (code in 200..299) "OK ($code): ${body?.take(120)}" else "HTTP $code: ${body?.take(120)}"
    } catch (e: Exception) {
        Log.w("Diagnostics", "health fetch failed", e)
        "Unreachable: ${e.message}"
    }
}