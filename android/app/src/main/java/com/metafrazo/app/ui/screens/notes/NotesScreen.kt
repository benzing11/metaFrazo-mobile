package com.metafrazo.app.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun NotesScreen(meetingCode: String, onBack: () -> Unit) {
    var content by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(meetingCode) {
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/notes") }
        if (code in 200..299) {
            val n = JSONObject(body).optJSONObject("note")
            if (n != null) content = n.optString("content", "")
        } else status = "Load failed: $code"
    }
    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        Text("Notes", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.weight(1f), label = { Text("Your notes") })
        Button(onClick = {
            kotlinx.coroutines.MainScope().launch {
                val (c, _) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$meetingCode/notes", "{\"content\":\"${content.replace("\"", "\\\"")}\"}") }
                status = if (c in 200..299) "Saved" else "Save failed: $c"
            }
        }) { Text("Save") }
        if (status.isNotEmpty()) Text(status)
        Button(onClick = onBack) { Text("Back") }
    }
}