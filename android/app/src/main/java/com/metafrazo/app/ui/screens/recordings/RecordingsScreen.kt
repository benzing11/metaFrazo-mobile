package com.metafrazo.app.ui.screens.recordings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.network.Api
import com.metafrazo.app.ui.components.EmptyState
import com.metafrazo.app.ui.components.ErrorState
import com.metafrazo.app.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class Recording(val id: String, val title: String, val status: String, val durationSeconds: Double, val createdAt: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    val items = remember { mutableStateListOf<Recording>() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        loading = true
        loadError = null
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/recordings") }
        if (code in 200..299) {
            items.clear()
            val arr = JSONObject(body).optJSONArray("recordings") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                items.add(
                    Recording(
                        id = r.optString("id"),
                        title = r.optString("meeting_title").ifBlank { r.optString("meeting_code").ifBlank { "Recording" } },
                        status = r.optString("status", "unknown"),
                        durationSeconds = r.optDouble("duration_seconds", 0.0),
                        createdAt = r.optString("created_at"),
                    ),
                )
            }
        } else {
            loadError = if (code == -1) body else "Could not load recordings (HTTP $code)"
        }
        loading = false
    }

    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("\"${rec.title}\" will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        val (c, body) = withContext(Dispatchers.IO) { Api.request("DELETE", "/api/recordings/${rec.id}") }
                        if (c in 200..299) items.remove(rec) else actionError = if (c == -1) body else "Delete failed (HTTP $c)"
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingState("Loading recordings…")
                loadError != null -> ErrorState(loadError!!, onRetry = { reload++ })
                items.isEmpty() -> EmptyState(
                    "No recordings yet",
                    "Start a recording from the meeting menu. Finished recordings show up here.",
                )
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    actionError?.let { err ->
                        item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(items, key = { it.id }) { r ->
                        Card(
                            Modifier.fillMaxWidth().clickable { onPlay(r.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${r.status.replaceFirstChar { it.uppercase() }} · ${formatDuration(r.durationSeconds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onPlay(r.id) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
                                IconButton(onClick = { pendingDelete = r }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
