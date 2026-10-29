package com.metafrazo.app.ui.screens.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val denied = res.filterValues { !it }.keys
        status = if (denied.isEmpty()) "All permissions granted" else "Denied: ${denied.joinToString()}"
    }
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text("Permissions", style = MaterialTheme.typography.headlineLarge)
        Text("MetaFrazi needs microphone and camera access for meetings, plus screen capture consent when sharing your screen.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { launcher.launch(perms) }) { Text("Request mic + camera") }
            Button(onClick = onBack) { Text("Back") }
        }
        Text(status)
        perms.forEach { p ->
            val granted = ContextCompat.checkSelfPermission(context, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Text("${p.substringAfterLast('.')}: ${if (granted) "granted" else "not granted"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}