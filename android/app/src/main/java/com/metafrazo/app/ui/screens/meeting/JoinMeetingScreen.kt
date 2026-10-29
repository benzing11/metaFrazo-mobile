package com.metafrazo.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.feature.meeting.MeetingViewModel

@Composable
fun JoinMeetingScreen(initialCode: String = "", onBack: () -> Unit, onConnected: () -> Unit = {}, vm: MeetingViewModel = viewModel(factory = MeetingViewModel.factory(LocalContext.current.applicationContext as android.app.Application), viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity)) {
    val state by vm.state.collectAsState()
    var code by remember { mutableStateOf(initialCode) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(state.status) { if (state.status == "connected") onConnected() }
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text("Join a meeting", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Meeting code or link") })
        Button(onClick = {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            vm.joinMeeting(code)
        }, enabled = code.isNotBlank() && state.status != "joining") { Text("Join") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.status == "connected") Text("Connected to ${state.code}", color = MaterialTheme.colorScheme.primary)
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}