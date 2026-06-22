package com.bambuprinterlan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.app.PrintHistoryStore
import com.bambuprinterlan.app.PrintRecord
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local print history — finished/failed jobs recorded from live status. */
@Composable
fun HistoryScreen(onBack: () -> Unit = {}) {
    val records by PrintHistoryStore.records.collectAsState()
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回") }
                BiText(Bi("Print history", "列印紀錄"),
                    enSize = MaterialTheme.typography.headlineSmall.fontSize)
            }
        }
        if (records.isEmpty()) {
            item { BiBody(Bi("No prints yet. Finished and failed jobs will appear here.",
                "未有紀錄。完成或失敗嘅任務會喺度顯示。")) }
        } else {
            item {
                TextButton(onClick = { PrintHistoryStore.clear() }) {
                    Text(Bi("Clear history", "清除紀錄").inline)
                }
            }
            items(records) { r -> HistoryRow(r, fmt.format(Date(r.timeMillis))) }
        }
    }
}

@Composable
private fun HistoryRow(r: PrintRecord, time: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (r.status == "finished") "✅" else "❌", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                val label = if (r.status == "finished") Bi("Finished", "完成") else Bi("Failed", "失敗")
                Text(label.inline + "  ·  " + time, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
