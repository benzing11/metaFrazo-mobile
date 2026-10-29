package com.metafrazo.app.ui.screens.captions

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.feature.captions.CaptionsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionsScreen(onBack: () -> Unit, vm: CaptionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsInitialized = remember { mutableStateOf(false) }
    val ttsReady = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.attachToRoom()
        tts.value = TextToSpeech(context) { status -> ttsInitialized.value = status == TextToSpeech.SUCCESS }
    }
    // Re-validate voice availability whenever the engine is ready or the target language changes.
    LaunchedEffect(ttsInitialized.value, state.targetLang) {
        val engine = tts.value
        if (!ttsInitialized.value || engine == null) {
            ttsReady.value = false
            return@LaunchedEffect
        }
        val r = engine.setLanguage(localeFor(state.targetLang))
        ttsReady.value = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }
    DisposableEffect(Unit) { onDispose { tts.value?.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live captions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Text("I speak", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en", "ta", "hi").forEach { lang ->
                    FilterChip(selected = state.spokenLang == lang, onClick = { vm.setSpokenLang(lang) }, label = { Text(lang.uppercase()) })
                }
            }
            Text("Translate to", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en", "ta", "hi").forEach { lang ->
                    FilterChip(selected = state.targetLang == lang, onClick = { vm.setTargetLang(lang) }, label = { Text(lang.uppercase()) })
                }
                FilterChip(selected = state.showOriginal, onClick = { vm.toggleOriginal() }, label = { Text("Original") })
            }
            Button(
                onClick = { if (state.listening) vm.stopListening() else vm.startListening() },
                enabled = state.spokenLang == state.targetLang || state.modelReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.listening) "Stop listening" else "Start listening") }
            if (state.status.isNotBlank()) {
                Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.listening) {
                Text(
                    "Listening in ${state.spokenLang.uppercase()}…" + if (state.partial.isNotBlank()) " ${state.partial}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.spokenLang == state.targetLang) {
                Text(
                    "Spoken and reading language match — captions stay untranslated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let { err ->
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.clearError(); vm.prepareModel() }) { Text("Retry") }
                }
            }
            if (state.captions.isEmpty()) {
                Text(
                    "Captions will appear here once someone speaks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.captions) { c ->
                    Column {
                        Text(c.speaker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (state.showOriginal) Text(c.original, style = MaterialTheme.typography.bodyLarge)
                        val translated = c.translations[state.targetLang] ?: c.original
                        if (state.targetLang != c.sourceLang) Text(translated, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (ttsInitialized.value && !ttsReady.value) {
                Text(
                    "No ${state.targetLang.uppercase()} voice installed on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    val last = state.captions.lastOrNull() ?: return@Button
                    val text = last.translations[state.targetLang] ?: last.original
                    tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "caption")
                },
                enabled = ttsReady.value && state.captions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speak last") }
        }
    }
}

private fun localeFor(lang: String): Locale = when (lang) {
    "ta" -> Locale("ta", "IN")
    "hi" -> Locale("hi", "IN")
    else -> Locale("en", "US")
}
