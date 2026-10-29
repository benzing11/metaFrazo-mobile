package com.metafrazo.app.ui.screens.transcripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import com.metafrazo.app.core.focus.FocusAssistant
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun TranscriptScreen(meetingCode: String, onBack: () -> Unit) {
    val segs = remember { mutableStateListOf<JSONObject>() }
    var status by remember { mutableStateOf("") }
    var showFocus by remember { mutableStateOf(false) }
    LaunchedEffect(meetingCode) {
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/transcripts") }
        if (code in 200..299) {
            val arr = JSONObject(body).optJSONArray("segments") ?: JSONArray()
            for (i in 0 until arr.length()) segs.add(arr.getJSONObject(i))
        } else status = "Load failed: $code"
    }
    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        Text("Transcript", style = MaterialTheme.typography.headlineLarge)
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(segs) { s ->
                Column {
                    Text(s.optString("original_text"), style = MaterialTheme.typography.bodyMedium)
                    val tr = s.optJSONArray("translations")
                    if (tr != null) for (i in 0 until tr.length()) {
                        val t = tr.getJSONObject(i)
                        Text("→ ${t.optString("target_language")}: ${t.optString("translated_text")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(onClick = { showFocus = !showFocus }) { Text(if (showFocus) "Hide focus" else "Focus assistant") }
        if (showFocus) {
            val combined = segs.joinToString(" ") { it.optString("original_text") }
            val items = FocusAssistant.analyze(combined)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Focus", style = MaterialTheme.typography.titleMedium)
                items.forEach { it -> Text("• [${it.type}] ${it.text}", style = MaterialTheme.typography.bodySmall) }
                if (items.isEmpty()) Text("No action items found yet", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}