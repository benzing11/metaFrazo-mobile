package com.metafrazo.app.ui.screens.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun readGranted(): Map<String, Boolean> = REQUIRED_PERMISSIONS.associateWith {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(readGranted()) }
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = readGranted()
        val denied = res.filterValues { !it }.keys
        status = if (denied.isEmpty()) "All permissions granted" else "Denied: ${denied.joinToString { it.substringAfterLast('.') }}"
    }
    val allGranted = granted.values.all { it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), Arrangement.spacedBy(12.dp)) {
            Text(
                "MetaFrazo needs microphone and camera access for meetings, plus screen capture consent when sharing your screen.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                    granted.forEach { (perm, ok) ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(perm.substringAfterLast('.').replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (ok) "Granted" else "Not granted",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Button(onClick = { launcher.launch(REQUIRED_PERMISSIONS) }, enabled = !allGranted, modifier = Modifier.fillMaxWidth()) {
                Text(if (allGranted) "Everything is set" else "Request mic + camera")
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
