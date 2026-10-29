package com.metafrazo.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.metafrazo.app.feature.meeting.MeetingViewModel
import com.metafrazo.app.ui.navigation.AppNavHost
import com.metafrazo.app.ui.theme.MetafrazoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startRoute = routeFromIntent(intent)
        setContent {
            MetafrazoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(startRoute = startRoute)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (MeetingViewModel.activeRoom != null) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (_: Exception) { }
        }
    }

    private fun routeFromIntent(intent: Intent?): String {
        val data: Uri? = intent?.data
        if (data != null) {
            val code = data.lastPathSegment
            if (!code.isNullOrBlank() && (data.path?.contains("/join") == true || data.host == "join")) {
                return "join/$code"
            }
        }
        return "home"
    }
}