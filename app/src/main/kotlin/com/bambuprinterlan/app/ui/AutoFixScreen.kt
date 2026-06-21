package com.bambuprinterlan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bambuprinterlan.app.AutoFixViewModel
import com.bambuprinterlan.app.FixTask
import com.bambuprinterlan.app.TaskStatus
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * Report-a-Bug + Auto-Fix Dashboard — add tasks, run them through the model
 * client with retries, see per-task progress/output. Bilingual.
 */
@Composable
fun AutoFixScreen(vm: AutoFixViewModel = viewModel(), onBack: () -> Unit = {}) {
    val tasks by vm.tasks.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回")
                }
                BiText(Bi("Auto-Fix Dashboard", "自動修復面板"),
                    enSize = MaterialTheme.typography.headlineSmall.fontSize)
            }
        }
        item { BiBody(Bi("Add tasks; each runs the model with retries.", "加入任務；每個會用模型重試執行。")) }
        item {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text(Bi("Describe a bug / task", "描述問題／任務").inline) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.add(input); input = "" }) {
                    Text(Bi("Add task", "加入任務").inline)
                }
                OutlinedButton(onClick = { vm.clear() }) { Text(Bi("Clear", "清除").inline) }
                Button(onClick = { vm.runAll() }, enabled = !busy && tasks.isNotEmpty()) {
                    Text((if (busy) Bi("Running…", "執行緊…") else Bi("Run all", "全部執行")).inline)
                }
            }
        }
        items(tasks) { task -> TaskCard(task) { vm.remove(task.id) } }
    }
}

@Composable
private fun TaskCard(task: FixTask, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.prompt, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                Text(statusLabel(task.status).inline, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove  移除")
                }
            }
            if (task.attempts > 0) {
                Text(Bi("Attempts", "嘗試次數").inline + ": ${task.attempts}",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (task.output.isNotBlank()) {
                Text(task.output, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun statusLabel(s: TaskStatus): Bi = when (s) {
    TaskStatus.PENDING -> Bi("Pending", "等待")
    TaskStatus.RUNNING -> Bi("In progress", "執行中")
    TaskStatus.RETRYING -> Bi("Retrying", "重試中")
    TaskStatus.DONE -> Bi("Done", "完成")
    TaskStatus.FAILED -> Bi("Failed", "失敗")
}
