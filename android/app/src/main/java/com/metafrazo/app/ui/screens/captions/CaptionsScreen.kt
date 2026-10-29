package com.metafrazo.app.ui.screens.captions

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
fun CaptionsScreen(onBack: () -> Unit, vm: CaptionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsReady = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.attachToRoom()
        tts.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.value?.setLanguage(localeFor(state.targetLang))
                ttsReady.value = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }
    DisposableEffect(Unit) { onDispose { tts.value?.shutdown() } }

    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        Text("Live captions", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en", "ta", "hi").forEach { lang ->
                FilterChip(selected = state.targetLang == lang, onClick = { vm.setTargetLang(lang) }, label = { Text(lang.uppercase()) })
            }
            FilterChip(selected = state.showOriginal, onClick = { vm.toggleOriginal() }, label = { Text("Original") })
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val last = state.captions.lastOrNull() ?: return@Button
                val text = last.translations[state.targetLang] ?: last.original
                val t = tts.value ?: return@Button
                t.language = localeFor(state.targetLang)
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "caption")
            }, enabled = ttsReady.value && state.captions.isNotEmpty()) { Text("Speak last") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

private fun localeFor(lang: String): Locale = when (lang) {
    "ta" -> Locale("ta", "IN")
    "hi" -> Locale("hi", "IN")
    else -> Locale("en", "US")
}