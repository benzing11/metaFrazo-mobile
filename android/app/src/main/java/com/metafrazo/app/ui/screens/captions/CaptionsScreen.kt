package com.metafrazo.app.ui.screens.captions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.core.translate.DeviceTranslator
import com.metafrazo.app.feature.captions.CaptionItem
import com.metafrazo.app.feature.captions.CaptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionsScreen(
    onBack: () -> Unit,
    vm: CaptionsViewModel = viewModel(
        factory = CaptionsViewModel.factory(LocalContext.current.applicationContext as android.app.Application),
    ),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Captions need the microphone for dictation; request it the first time the screen opens.
    val micPermission = remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
    ) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPermission.value = granted
        if (!granted) vm.onMicPermissionDenied()
    }

    LaunchedEffect(Unit) {
        vm.attachToRoom()
        vm.prepareModel()
        if (!micPermission.value) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

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
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.size(4.dp))
            LanguagePicker("I speak", DeviceTranslator.supported, state.spokenLang) { vm.setSpokenLang(it) }
            LanguagePicker("Translate to", DeviceTranslator.supported, state.targetLang) { vm.setTargetLang(it) }

            // Speak-aloud toggle: translations are read out loud as they arrive.
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Speak translations aloud", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Read incoming captions in ${DeviceTranslator.labels[state.targetLang] ?: state.targetLang.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.speakTranslations, onCheckedChange = { vm.setSpeakTranslations(it) })
                }
            }

            // Auto-detect toggle: let Whisper pick the spoken language per phrase, limited to
            // English, Tamil, and Hindi, so mixed-language speech transcribes correctly.
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Auto-detect language", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Detect English, Tamil, or Hindi per phrase (best for mixed speech)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.autoDetectLang, onCheckedChange = { vm.setAutoDetectLang(it) })
                }
            }

            // Voice replacement: mute the raw mic so others hear only the spoken translation.
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.micMutedForReplacement) Icons.Filled.MicOff else Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Replace my voice", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (state.micMutedForReplacement) {
                                "Mic muted — others hear the spoken translation"
                            } else {
                                "Mute my mic; others hear only the translated speech while captions run"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.voiceReplacement, onCheckedChange = { vm.setVoiceReplacement(it) })
                }
            }

            ListenButton(
                listening = state.listening,
                enabled = state.spokenLang == state.targetLang || state.modelReady,
                onClick = {
                    when {
                        state.listening -> vm.stopListening()
                        !micPermission.value -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> vm.startListening()
                    }
                },
            )

            StatusLine(state)

            state.error?.let { err ->
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.clearError(); vm.prepareModel() }) { Text("Retry") }
                }
            }

            CaptionList(
                captions = state.captions,
                targetLang = state.targetLang,
                showOriginal = state.showOriginal,
                livePartialOriginal = state.partial,
                livePartialTranslated = state.partialTranslated,
                listening = state.listening,
                modifier = Modifier.weight(1f),
            )

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.showOriginal,
                    onClick = { vm.toggleOriginal() },
                    label = { Text("Show original") },
                )
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { lang ->
                FilterChip(
                    selected = selected == lang,
                    onClick = { onSelect(lang) },
                    label = { Text(DeviceTranslator.labels[lang] ?: lang.uppercase()) },
                )
            }
        }
    }
}

@Composable
private fun ListenButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = if (listening) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(if (listening) "Stop listening" else "Start listening")
    }
}

@Composable
private fun StatusLine(state: com.metafrazo.app.feature.captions.CaptionsUiState) {
    when {
        state.listening -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(active = state.hearing)
                val langLabel = if (state.autoDetectLang) "EN/TA/HI" else state.spokenLang.uppercase()
                Text(
                    when {
                        state.hearing -> "Transcribing…"
                        else -> "Listening ($langLabel)…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        state.spokenLang == state.targetLang && state.status.isNotBlank() -> {
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.status.isNotBlank() -> {
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pulsing dot: red while actively hearing speech, dim otherwise. */
@Composable
private fun LiveDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "livedot")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .size(10.dp)
            .alpha(if (active) pulse else 0.5f)
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun CaptionList(
    captions: List<CaptionItem>,
    targetLang: String,
    showOriginal: Boolean,
    livePartialOriginal: String,
    livePartialTranslated: String,
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val hasPartial = listening && livePartialOriginal.isNotBlank()

    // Keep the newest caption (or the live partial) in view as content grows.
    LaunchedEffect(captions.size, hasPartial, livePartialOriginal) {
        val count = captions.size + if (hasPartial) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    if (captions.isEmpty() && !hasPartial) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Captions appear here once someone speaks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier.fillMaxWidth(), listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(captions) { c ->
            CaptionBubble(c, targetLang, showOriginal)
        }
        if (hasPartial) {
            item {
                CaptionBubble(
                    CaptionItem(
                        speaker = "You",
                        sourceLang = "",
                        original = livePartialOriginal,
                        translations = if (livePartialTranslated.isNotBlank()) mapOf(targetLang to livePartialTranslated) else emptyMap(),
                        ts = Double.MAX_VALUE,
                        isLocal = true,
                    ),
                    targetLang,
                    showOriginal,
                    live = true,
                )
            }
        }
    }
}

@Composable
private fun CaptionBubble(
    c: CaptionItem,
    targetLang: String,
    showOriginal: Boolean,
    live: Boolean = false,
) {
    val sameLanguage = targetLang == c.sourceLang
    val translated = c.translations[targetLang]
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (c.isLocal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().alpha(if (live) 0.7f else 1f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                c.speaker,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (sameLanguage) {
                Text(c.original, style = MaterialTheme.typography.bodyLarge)
            } else {
                if (showOriginal && c.original.isNotBlank()) {
                    Text(
                        c.original,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    translated ?: c.original,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (c.isLocal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
