package com.bambuprinterlan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bambuprinterlan.app.BatchViewModel
import com.bambuprinterlan.app.QueueItem
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * Batch Printer Sender — queue many sliced .3mf files and upload them to the
 * printer SD over FTPS. Every label is bilingual.
 */
@Composable
fun BatchSenderScreen(vm: BatchViewModel = viewModel(), onBack: () -> Unit = {}) {
    val queue by vm.queue.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var ip by remember { mutableStateOf("") }
    var accessCode by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.add(uris) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回")
                }
                BiText(Bi("Batch Printer Sender", "批次列印傳送"),
                    enSize = MaterialTheme.typography.headlineSmall.fontSize)
            }
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.labelLarge) } }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiText(Bi("Printer (LAN)", "打印機（LAN）"))
                    BiBody(Bi("Upload sliced files to the printer SD.", "將切片檔上載去打印機 SD。"))
                    OutlinedTextField(ip, { ip = it }, singleLine = true,
                        label = { Text(Bi("Printer IP", "打印機 IP").inline) },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(accessCode, { accessCode = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(Bi("Access code", "存取碼").inline) },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(serial, { serial = it }, singleLine = true,
                        label = { Text(Bi("Serial (optional)", "序號（可選）").inline) },
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(Bi("Add files", "加入檔案").inline)
                }
                OutlinedButton(onClick = { vm.clear() }) {
                    Text(Bi("Clear", "清除").inline)
                }
                Button(onClick = { vm.uploadAll(ip, accessCode, serial) }, enabled = !busy) {
                    Text(Bi("Upload all", "全部上載").inline)
                }
            }
        }

        items(queue) { item -> QueueRow(item) }
    }
}

@Composable
private fun QueueRow(item: QueueItem) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(stateLabel(item).inline, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun stateLabel(item: QueueItem): Bi = when (item.state) {
    com.bambuprinterlan.app.QueueState.PENDING -> Bi("Pending", "等待")
    com.bambuprinterlan.app.QueueState.UPLOADING -> Bi("Uploading…", "上載中…")
    com.bambuprinterlan.app.QueueState.DONE -> Bi("Done", "完成")
    com.bambuprinterlan.app.QueueState.FAILED -> Bi("Failed", "失敗")
}
