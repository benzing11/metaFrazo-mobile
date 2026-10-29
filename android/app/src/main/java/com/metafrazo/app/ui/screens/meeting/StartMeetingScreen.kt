package com.metafrazo.app.ui.screens.meeting

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.feature.meeting.MeetingViewModel

@Composable
fun StartMeetingScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit = {},
    vm: MeetingViewModel = viewModel(
        factory = MeetingViewModel.factory(LocalContext.current.applicationContext as android.app.Application),
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    MeetingLobby(mode = LobbyMode.Start, vm = vm, onBack = onBack, onConnected = onConnected)
}
