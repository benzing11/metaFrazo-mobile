package com.metafrazo.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.auth.AuthRepository
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val signedIn = AuthRepository.isSignedIn(context)
    val name = AuthRepository.displayName(context)
    var backendUrl by remember { mutableStateOf(Api.backendUrl) }
    var aiUrl by remember { mutableStateOf(Api.aiUrl) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            Arrangement.spacedBy(12.dp),
        ) {
            Text("Account: $name", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (signedIn) "Signed in. Backend calls use your ID token."
                else "Guest (dev) mode. Firebase/Credential Manager groundwork is pending google-services config.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { AuthRepository.signOut(context) }) { Text("Sign out / switch to guest") }
            Text("Network", style = MaterialTheme.typography.titleMedium)
            Text(
                "USB: use http://127.0.0.1 with adb reverse. Wireless: use your computer’s LAN IP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Effective backend: ${Api.backendUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Backend URL") })
            OutlinedTextField(value = aiUrl, onValueChange = { aiUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("AI service URL") })
            Button(onClick = {
                if (!Api.isValidUrl(backendUrl) || !Api.isValidUrl(aiUrl)) {
                    Toast.makeText(context, "Enter valid http(s) URLs", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                Api.setUrls(context, backendUrl, aiUrl)
                backendUrl = Api.backendUrl
                aiUrl = Api.aiUrl
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Api.checkBackend() }
                    Toast.makeText(
                        context,
                        if (ok) "Saved. Backend reachable." else "Saved, but backend is not reachable. Check it is running and adb reverse includes tcp:3000.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }) { Text("Save network URLs") }
            Button(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Api.checkBackend() }
                    Toast.makeText(
                        context,
                        if (ok) "Backend reachable at ${Api.backendUrl}."
                        else "Backend not reachable at ${Api.backendUrl}. On USB run adb reverse tcp:3000 tcp:3000; on Wi-Fi set your LAN URL.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }) { Text("Test connection") }
            Button(onClick = {
                Api.setUrls(context, Api.DEFAULT_USB_BACKEND, Api.DEFAULT_USB_AI)
                backendUrl = Api.backendUrl
                aiUrl = Api.aiUrl
                Toast.makeText(context, "Reset to USB defaults", Toast.LENGTH_SHORT).show()
            }) { Text("Reset to USB defaults") }
        }
    }
}
