package com.metafrazo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.metafrazo.app.ui.screens.captions.CaptionsScreen
import com.metafrazo.app.ui.screens.chat.ChatScreen
import com.metafrazo.app.ui.screens.notes.NotesScreen
import com.metafrazo.app.ui.screens.permissions.PermissionsScreen
import com.metafrazo.app.ui.screens.transcripts.TranscriptScreen
import com.metafrazo.app.ui.screens.recordings.PlayerScreen
import com.metafrazo.app.ui.screens.recordings.RecordingsScreen
import com.metafrazo.app.ui.screens.diagnostics.DiagnosticsScreen
import com.metafrazo.app.ui.screens.settings.SettingsScreen
import com.metafrazo.app.ui.screens.home.HomeScreen
import com.metafrazo.app.ui.screens.meeting.JoinMeetingScreen
import com.metafrazo.app.ui.screens.meeting.MeetingRoomScreen
import com.metafrazo.app.ui.screens.meeting.StartMeetingScreen

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController(), startRoute: String = "home") {
    NavHost(navController = navController, startDestination = startRoute) {
        composable("home") {
            HomeScreen(
                onStartMeeting = { navController.navigate("start") },
                onJoinMeeting = { navController.navigate("join") },
                onRecordings = { navController.navigate("recordings") },
                onPermissions = { navController.navigate("permissions") },
                onDiagnostics = { navController.navigate("diagnostics") },
                onSettings = { navController.navigate("settings") },
            )
        }
        // Replace the lobby with the room so Back from the room never lands on a stale lobby.
        val toRoom: () -> Unit = {
            navController.navigate("room") {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
        val leaveRoom: () -> Unit = {
            if (!navController.popBackStack("home", false)) {
                navController.navigate("home") { popUpTo(0); launchSingleTop = true }
            }
        }
        composable("start") { StartMeetingScreen(onBack = { navController.popBackStack() }, onConnected = toRoom) }
        composable("join") { JoinMeetingScreen(onBack = { navController.popBackStack() }, onConnected = toRoom) }
        composable(
            "join/{code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { backStack ->
            val code = backStack.arguments?.getString("code").orEmpty()
            JoinMeetingScreen(initialCode = code, onBack = { navController.popBackStack() }, onConnected = toRoom)
        }
        composable("room") { MeetingRoomScreen(onLeave = leaveRoom, onCaptions = { navController.navigate("captions") }, onChat = { code -> navController.navigate("chat/$code") }, onNotes = { code -> navController.navigate("notes/$code") }, onTranscripts = { code -> navController.navigate("transcripts/$code") }) }
        composable("transcripts/{code}", arguments = listOf(navArgument("code") { type = NavType.StringType })) { bs ->
            val code = bs.arguments?.getString("code").orEmpty()
            TranscriptScreen(meetingCode = code, onBack = { navController.popBackStack() })
        }
        composable("notes/{code}", arguments = listOf(navArgument("code") { type = NavType.StringType })) { bs ->
            val code = bs.arguments?.getString("code").orEmpty()
            NotesScreen(meetingCode = code, onBack = { navController.popBackStack() })
        }
        composable("chat/{code}", arguments = listOf(navArgument("code") { type = NavType.StringType })) { bs ->
            val code = bs.arguments?.getString("code").orEmpty()
            ChatScreen(meetingCode = code, onBack = { navController.popBackStack() })
        }
        composable("captions") { CaptionsScreen(onBack = { navController.popBackStack() }) }
        composable("permissions") { PermissionsScreen(onBack = { navController.popBackStack() }) }
        composable("diagnostics") { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable("recordings") { RecordingsScreen(onBack = { navController.popBackStack() }, onPlay = { id -> navController.navigate("player/$id") }) }
        composable("player/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { bs ->
            val id = bs.arguments?.getString("id").orEmpty()
            PlayerScreen(recordingId = id, onBack = { navController.popBackStack() })
        }
    }
}
