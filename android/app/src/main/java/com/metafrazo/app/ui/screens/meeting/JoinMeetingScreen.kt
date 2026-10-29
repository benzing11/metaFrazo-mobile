package com.metafrazo.app.ui.screens.meeting

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metafrazo.app.feature.meeting.MeetingViewModel

@Composable
fun JoinMeetingScreen(
    initialCode: String = "",
    onBack: () -> Unit,
    onConnected: () -> Unit = {},
    vm: MeetingViewModel = viewModel(
        factory = MeetingViewModel.factory(LocalContext.current.applicationContext as android.app.Application),
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    MeetingLobby(mode = LobbyMode.Join, vm = vm, initialCode = initialCode, onBack = onBack, onConnected = onConnected)
}
