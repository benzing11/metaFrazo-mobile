package com.metafrazo.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.core.network.Api
import com.metafrazo.app.core.speech.SpeechSynthesizer
import com.metafrazo.app.core.translate.DeviceTranslator
import com.metafrazo.app.feature.captions.CaptionsViewModel
import com.metafrazo.app.ui.components.EmptyState
import com.metafrazo.app.ui.components.ErrorState
import com.metafrazo.app.ui.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val POLL_INTERVAL_MS = 4_000L

private data class Message(
    val id: String,
    val sender: String,
    val text: String,
    val mine: Boolean,
    val failed: Boolean = false,
    val lang: String = "en",
    val assistant: Boolean = false,
    val citations: Int = 0,
    val translated: String? = null,
    /** Reading language [translated] was produced for, so a language change re-translates. */
    val translatedFor: String? = null,
)

private fun parseMessages(body: String): List<Message> {
    val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
    return (0 until arr.length()).map { i ->
        val m = arr.getJSONObject(i)
        val sender = m.optString("sender_user_id")
        val assistant = m.optBoolean("is_assistant", false)
        Message(
            id = m.optString("id"),
            sender = if (assistant) "Assistant" else sender,
            text = m.optString("original_text"),
            mine = !assistant && sender == Api.devUserId,
            lang = m.optString("source_language", "en"),
            assistant = assistant,
            citations = m.optJSONArray("cited_segment_ids")?.length() ?: 0,
        )
    }
}

private fun localId() = "local-${System.nanoTime()}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(meetingCode: String, onBack: () -> Unit) {
    val messages = remember { mutableStateListOf<Message>() }
    val spoken = remember { mutableSetOf<String>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var askMode by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val captionsVm: CaptionsViewModel = viewModel(
        factory = CaptionsViewModel.factory(context.applicationContext as android.app.Application),
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
    )
    val captionsState by captionsVm.state.collectAsState()
    val readLang = captionsState.targetLang
    val speakLang = captionsState.spokenLang
    val speakChat = captionsState.speakChat

    val synth = remember {
        SpeechSynthesizer(
            context = context.applicationContext,
            onBusyChanged = { busy -> captionsVm.setTtsBusy(busy) },
            onUnavailable = { msg -> notice = msg },
        )
    }
    DisposableEffect(synth) {
        onDispose {
            synth.shutdown()
            captionsVm.setTtsBusy(false)
        }
    }

    // Initial load, then poll so other people's messages show up (and can be read aloud) without a manual refresh.
    LaunchedEffect(meetingCode, reload) {
        loading = true
        loadError = null
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/chat") }
        if (code in 200..299) {
            messages.clear()
            messages.addAll(parseMessages(body))
            // History is never read aloud; only messages that arrive from now on.
            spoken.clear()
            spoken.addAll(messages.map { it.id })
        } else {
            loadError = if (code == -1) body else "Could not load chat (HTTP $code)"
        }
        loading = false
        if (loadError != null) return@LaunchedEffect
        while (true) {
            delay(POLL_INTERVAL_MS)
            val (c, b) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/chat") }
            if (c !in 200..299) continue
            val known = messages.map { it.id }.toSet()
            parseMessages(b).filter { it.id !in known }.forEach { messages.add(it) }
        }
    }

    // Translate into the reading language on-device, then read aloud if enabled.
    LaunchedEffect(messages.size, readLang, loading, speakChat) {
        if (loading) return@LaunchedEffect
        // Snapshot the ids so new polled messages don't shift indices mid-loop; look each up by id.
        val ids = messages.map { it.id }
        for (id in ids) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx < 0) continue
            val m = messages[idx]
            if (!m.mine && !m.failed) {
                if (m.lang == readLang) {
                    if (m.translated != null) {
                        val at = messages.indexOfFirst { it.id == id }
                        if (at >= 0) messages[at] = messages[at].copy(translated = null, translatedFor = null)
                    }
                } else if (m.translatedFor != readLang && DeviceTranslator.prepare(m.lang, readLang) == null) {
                    val t = DeviceTranslator.translate(m.text, m.lang, readLang)
                    val at = messages.indexOfFirst { it.id == id }
                    if (at >= 0 && t.isNotBlank() && t != m.text) {
                        messages[at] = messages[at].copy(translated = t, translatedFor = readLang)
                    }
                }
            }
            val cur = messages.firstOrNull { it.id == id }
            if (cur != null && cur.id !in spoken) {
                if (!speakChat || cur.mine || cur.failed) {
                    spoken.add(cur.id)
                } else {
                    spoken.add(cur.id)
                    if (cur.translated != null) synth.speak(cur.translated, readLang) else synth.speak(cur.text, cur.lang)
                }
            }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Swap a just-sent message's temporary id for the server's, unless polling already added the real one.
    fun settle(localId: String, serverId: String) {
        if (serverId.isBlank()) return
        val idx = messages.indexOfFirst { it.id == localId }
        if (idx < 0) return
        if (messages.any { it.id == serverId }) messages.removeAt(idx) else messages[idx] = messages[idx].copy(id = serverId)
        spoken.add(serverId)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || asking) return
        input = ""
        val lid = localId()
        spoken.add(lid)
        messages.add(Message(lid, "You", text, mine = true, lang = speakLang))
        if (askMode) {
            scope.launch {
                asking = true
                val payload = JSONObject().put("question", text).put("source_lang", speakLang).toString()
                val (c, body) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$meetingCode/ask", payload) }
                if (c in 200..299) {
                    val root = JSONObject(body)
                    settle(lid, root.optJSONObject("question")?.optString("id").orEmpty())
                    val a = root.getJSONObject("answer")
                    val aid = a.optString("id")
                    if (messages.none { it.id == aid }) {
                        messages.add(
                            Message(
                                aid, "Assistant", a.optString("text"), mine = false, lang = "en",
                                assistant = true, citations = a.optJSONArray("cited_segment_ids")?.length() ?: 0,
                            ),
                        )
                    }
                } else {
                    val err = runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: if (c == -1) body else "Could not get an answer (HTTP $c)"
                    messages.add(Message(localId(), "Assistant", err, mine = false, failed = true, assistant = true))
                }
                asking = false
            }
            return
        }
        scope.launch {
            val payload = JSONObject().put("text", text).put("source_lang", speakLang).toString()
            val (c, body) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$meetingCode/chat", payload) }
            if (c in 200..299) {
                settle(lid, runCatching { JSONObject(body).optString("id") }.getOrDefault(""))
            } else {
                val idx = messages.indexOfFirst { it.id == lid }
                if (idx >= 0) messages[idx] = messages[idx].copy(failed = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat · $meetingCode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Box {
                        TextButton(onClick = { langMenu = true }) {
                            Text(DeviceTranslator.labels[readLang] ?: readLang.uppercase())
                        }
                        DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                            DropdownMenuItem(text = { Text("Read messages in", style = MaterialTheme.typography.labelMedium) }, onClick = {}, enabled = false)
                            DeviceTranslator.supported.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text((DeviceTranslator.labels[lang] ?: lang.uppercase()) + if (lang == readLang) "  ✓" else "") },
                                    onClick = { captionsVm.setTargetLang(lang); langMenu = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("I write in", style = MaterialTheme.typography.labelMedium) }, onClick = {}, enabled = false)
                            DeviceTranslator.supported.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text((DeviceTranslator.labels[lang] ?: lang.uppercase()) + if (lang == speakLang) "  ✓" else "") },
                                    onClick = { captionsVm.setSpokenLang(lang); langMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (speakChat) synth.stop()
                        captionsVm.setSpeakChat(!speakChat)
                    }) {
                        Icon(
                            if (speakChat) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (speakChat) "Stop reading new messages aloud" else "Read new messages aloud",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .imePadding(),
        ) {
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
                        itemsIndexed(messages, key = { _, m -> m.id }) { _, m ->
                            MessageBubble(
                                m,
                                readLang = readLang,
                                onSpeak = {
                                    if (m.translated != null) synth.speak(m.translated, readLang) else synth.speak(m.text, m.lang)
                                },
                            )
                        }
                    }
                }
            }
            notice?.let {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { notice = null }) { Text("Dismiss") }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                FilterChip(
                    selected = askMode,
                    onClick = { askMode = !askMode },
                    label = { Text(if (askMode) "Asking about this meeting" else "Ask about this meeting") },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (askMode) "Ask a question about the transcript" else "Message") },
                    maxLines = 4,
                )
                FilledIconButton(onClick = { send() }, enabled = input.isNotBlank() && !asking) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: Message, readLang: String, onSpeak: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    when {
                        m.failed -> MaterialTheme.colorScheme.errorContainer
                        m.assistant -> MaterialTheme.colorScheme.tertiaryContainer
                        m.mine -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!m.mine) {
                Text(m.sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val bubbleContentColor = when {
                m.failed -> MaterialTheme.colorScheme.onErrorContainer
                m.assistant -> MaterialTheme.colorScheme.onTertiaryContainer
                m.mine -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(m.text, style = MaterialTheme.typography.bodyLarge, color = bubbleContentColor)
            if (m.translated != null) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = bubbleContentColor.copy(alpha = 0.25f))
                Spacer(Modifier.height(6.dp))
                Text(
                    DeviceTranslator.labels[readLang] ?: readLang.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = bubbleContentColor.copy(alpha = 0.7f),
                )
                Text(
                    m.translated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = bubbleContentColor,
                )
            }
            if (m.assistant && m.citations > 0) {
                Text(
                    "Based on ${m.citations} transcript segment${if (m.citations == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (m.failed && !m.assistant) {
                Text("Not sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            if (!m.failed) {
                Box(
                    Modifier
                        .align(Alignment.End)
                        // 48dp minimum touch target while the visual stays a 32dp chip.
                        .minimumInteractiveComponentSize()
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onSpeak),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read this message aloud",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
