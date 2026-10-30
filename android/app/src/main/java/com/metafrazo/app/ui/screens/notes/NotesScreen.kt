package com.metafrazo.app.ui.screens.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.network.Api
import com.metafrazo.app.ui.components.ErrorState
import com.metafrazo.app.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(meetingCode: String, onBack: () -> Unit) {
    var content by remember { mutableStateOf("") }
    var lastSaved by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saveStatus by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(meetingCode, reload) {
        loading = true
        loadError = null
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/notes") }
        if (code in 200..299) {
            val n = JSONObject(body).optJSONObject("note")
            content = n?.optString("content", "") ?: ""
            lastSaved = content
        } else {
            loadError = if (code == -1) body else "Could not load notes (HTTP $code)"
        }
        loading = false
    }

    // Autosave: wait for a 1s pause in typing, then persist.
    LaunchedEffect(content) {
        if (loading || content == lastSaved) return@LaunchedEffect
        saveStatus = "Unsaved changes"
        delay(1000)
        saveStatus = "Saving…"
        val payload = JSONObject().put("content", content).toString()
        val (c, body) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$meetingCode/notes", payload) }
        if (c in 200..299) {
            lastSaved = content
            saveStatus = "Saved"
        } else {
            saveStatus = if (c == -1) "Not saved: $body" else "Not saved (HTTP $c). Will retry when you keep typing."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes · $meetingCode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when {
            loading -> LoadingState("Loading notes…", Modifier.padding(padding))
            loadError != null -> ErrorState(loadError!!, onRetry = { reload++ }, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .imePadding()
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text("Write your private notes for this meeting…") },
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        saveStatus.ifBlank { "Autosaves as you type" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (saveStatus.startsWith("Not saved")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
