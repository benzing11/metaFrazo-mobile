package com.metafrazo.app.ui.screens.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun RecordingsScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    val items = remember { mutableStateListOf<JSONObject>() }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val (code, body) = withContext(Dispatchers.IO) { Api.get("/api/recordings") }
        if (code in 200..299) {
            val arr = JSONObject(body).optJSONArray("recordings") ?: JSONArray()
            for (i in 0 until arr.length()) items.add(arr.getJSONObject(i))
        } else status = "Load failed: $code"
    }
    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        Text("Recordings", style = MaterialTheme.typography.headlineLarge)
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.optString("meeting_title", r.optString("meeting_code")), style = MaterialTheme.typography.titleMedium)
                        Text("${r.optString("status")} • ${r.optDouble("duration_seconds")}s", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onPlay(r.optString("id")) }) { Text("Play") }
                }
            }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}