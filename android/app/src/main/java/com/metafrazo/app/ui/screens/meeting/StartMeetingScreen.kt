package com.metafrazo.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.feature.meeting.MeetingViewModel

@Composable
fun StartMeetingScreen(onBack: () -> Unit, onConnected: () -> Unit = {}, vm: MeetingViewModel = viewModel(factory = MeetingViewModel.factory(LocalContext.current.applicationContext as android.app.Application), viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity)) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.status) { if (state.status == "connected") onConnected() }
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text("Start a meeting", style = MaterialTheme.typography.headlineLarge)
        Button(onClick = { vm.createMeeting() }, enabled = state.status != "creating") { Text("Create meeting") }
        state.code?.let { code ->
            Text("Meeting code: $code", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy code") }
                Button(onClick = { vm.joinMeeting(code) }) { Text("Join now") }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}