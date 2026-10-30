package com.metafrazo.app.ui.screens.diagnostics

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit = {}) {
    var backend by remember { mutableStateOf<String?>(null) }
    var ai by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }

    // Read the runtime URLs so the probe matches what the rest of the app actually uses.
    val backendUrl = Api.backendUrl
    val aiUrl = Api.aiUrl

    LaunchedEffect(tick, backendUrl, aiUrl) {
        loading = true
        backend = fetchHealth("$backendUrl/api/health")
        ai = fetchHealth("$aiUrl/health")
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backend: $backendUrl", style = MaterialTheme.typography.bodyMedium)
            if (loading) CircularProgressIndicator() else Text(backend ?: "unknown")
            Text("AI service: $aiUrl", style = MaterialTheme.typography.bodyMedium)
            if (!loading) Text(ai ?: "unknown")
            Button(onClick = { tick++ }) { Text("Refresh") }
        }
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
