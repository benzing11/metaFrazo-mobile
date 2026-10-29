package com.metafrazo.app.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metafrazo.app.core.network.Api
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ChatScreen(meetingCode: String, onBack: () -> Unit) {
    val messages = remember { mutableStateListOf<JSONObject>() }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(meetingCode) {
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/meetings/$meetingCode/chat") }
        if (code in 200..299) {
            val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
            for (i in 0 until arr.length()) messages.add(arr.getJSONObject(i))
        } else status = "Load failed: $code"
    }

    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        Text("Chat", style = MaterialTheme.typography.headlineLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(messages) { m ->
                Text("${m.optString("sender_user_id")}: ${m.optString("original_text")}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), label = { Text("Message") })
            Button(onClick = {
                if (input.isBlank()) return@Button
                val text = input
                input = ""
                kotlinx.coroutines.MainScope().launch {
                    val (c, body) = withContext(Dispatchers.IO) { Api.post("/api/meetings/$meetingCode/chat", "{\"text\":\"$text\",\"source_lang\":\"en\"}") }
                    if (c in 200..299) {
                        messages.add(JSONObject().put("sender_user_id", "me").put("original_text", text))
                    } else status = "Send failed: $c"
                }
            }) { Text("Send") }
        }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.error)
        Button(onClick = onBack) { Text("Back") }
    }
}