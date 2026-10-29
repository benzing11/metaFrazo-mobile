package com.metafrazo.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private data class Message(val sender: String, val text: String, val mine: Boolean, val failed: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(meetingCode: String, onBack: () -> Unit) {
    val messages = remember { mutableStateListOf<Message>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(meetingCode, reload) {
        loading = true
        loadError = null
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/chat") }
        if (code in 200..299) {
            messages.clear()
            val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val sender = m.optString("sender_user_id")
                messages.add(Message(sender, m.optString("original_text"), mine = sender == "dev-user"))
            }
        } else {
            loadError = if (code == -1) body else "Could not load chat (HTTP $code)"
        }
        loading = false
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        val index = messages.size
        messages.add(Message("You", text, mine = true))
        scope.launch {
            val payload = JSONObject().put("text", text).put("source_lang", "en").toString()
            val (c, _) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$meetingCode/chat", payload) }
            if (c !in 200..299 && index < messages.size) messages[index] = messages[index].copy(failed = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat · $meetingCode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    loading -> LoadingState("Loading chat…")
                    loadError != null -> ErrorState(loadError!!, onRetry = { reload++ })
                    messages.isEmpty() -> EmptyState("No messages yet", "Say hello. Messages are visible to everyone in this meeting.")
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(messages) { m -> MessageBubble(m) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 4,
                )
                FilledIconButton(onClick = { send() }, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    when {
                        m.failed -> MaterialTheme.colorScheme.errorContainer
                        m.mine -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!m.mine) {
                Text(m.sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
            if (m.failed) {
                Text("Not sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
