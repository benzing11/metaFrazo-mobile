package com.metafrazo.app.ui.screens.transcripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.focus.FocusAssistant
import com.metafrazo.app.core.network.Api
import com.metafrazo.app.ui.components.EmptyState
import com.metafrazo.app.ui.components.ErrorState
import com.metafrazo.app.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class Segment(val speaker: String, val lang: String, val text: String, val translations: Map<String, String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(meetingCode: String, onBack: () -> Unit) {
    val segments = remember { mutableStateListOf<Segment>() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showFocus by remember { mutableStateOf(false) }

    LaunchedEffect(meetingCode, reload) {
        loading = true
        loadError = null
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/transcripts") }
        if (code in 200..299) {
            segments.clear()
            val arr = JSONObject(body).optJSONArray("segments") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val tr = mutableMapOf<String, String>()
                s.optJSONArray("translations")?.let { t ->
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        tr[o.optString("target_language")] = o.optString("translated_text")
                    }
                }
                segments.add(
                    Segment(
                        speaker = s.optString("speaker_user_id").ifBlank { "Speaker" },
                        lang = s.optString("source_language", "en"),
                        text = s.optString("original_text"),
                        translations = tr,
                    ),
                )
            }
        } else {
            loadError = if (code == -1) body else "Could not load transcript (HTTP $code)"
        }
        loading = false
    }

    val visible = if (query.isBlank()) segments.toList() else segments.filter {
        it.text.contains(query, ignoreCase = true) || it.translations.values.any { v -> v.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcript · $meetingCode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search transcript") },
                    singleLine = true,
                )
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showFocus, onClick = { showFocus = !showFocus }, label = { Text("Focus assistant") })
                AssistChip(onClick = { reload++ }, label = { Text("Refresh") })
            }
            Box(Modifier.weight(1f)) {
                when {
                    loading -> LoadingState("Loading transcript…")
                    loadError != null -> ErrorState(loadError!!, onRetry = { reload++ })
                    segments.isEmpty() -> EmptyState(
                        "No transcript yet",
                        "Transcript segments appear here once captions are saved for this meeting.",
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showFocus) {
                            item {
                                val items = FocusAssistant.analyze(segments.joinToString(" ") { it.text })
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Focus", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        if (items.isEmpty()) {
                                            Text("No action items found yet", style = MaterialTheme.typography.bodySmall)
                                        }
                                        items.forEach {
                                            Text("• [${it.type}] ${it.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                        items(visible) { s ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${s.speaker} · ${s.lang.uppercase()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(s.text, style = MaterialTheme.typography.bodyLarge)
                                    s.translations.forEach { (lang, text) ->
                                        Text("${lang.uppercase()}: $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (visible.isEmpty()) {
                            item { Text("No matches for \"$query\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
