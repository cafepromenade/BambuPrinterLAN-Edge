package com.bambuprinterlan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bambuprinterlan.app.ImportedModel
import com.bambuprinterlan.app.PrepareViewModel
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * Prepare 準備 — import models (SAF), then slice. Slicing runs on the native
 * engine (Phase 1). Every label is bilingual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrepareScreen(
    vm: PrepareViewModel = viewModel(),
    onOpenHub: () -> Unit = {},
    onOpenPreview: () -> Unit = {},
    onOpenEditor: () -> Unit = {},
) {
    val models by vm.models.collectAsState()
    val message by vm.message.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.sliced.collect { onOpenPreview() } }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.import(it) } }

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importMany(uris) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BiText(Bi("Prepare", "準備"), enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }
        item {
            BiBody(Bi("How it works: 1) Import a model  2) Slice  3) Preview  4) Print on your printer.",
                "用法：1) 匯入模型  2) 切片  3) 預覽  4) 喺打印機列印。"))
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.labelLarge) } }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiText(Bi("Import model", "匯入模型"))
                    BiBody(Bi("STL · OBJ · 3MF · G-code", "STL · OBJ · 3MF · G-code"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            picker.launch(arrayOf(
                                "application/sla", "model/stl", "model/obj", "model/3mf",
                                "application/vnd.ms-3mfdocument", "application/step", "*/*",
                            ))
                        }) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = "Import")
                            Text("  " + Bi("Import a model", "匯入模型").inline)
                        }
                        androidx.compose.material3.OutlinedButton(onClick = {
                            multiPicker.launch(arrayOf("*/*"))
                        }) {
                            Text(Bi("Import many", "批次匯入").inline)
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onOpenHub) {
                            Text(Bi("File Hub", "檔案中心").inline)
                        }
                    }
                }
            }
        }

        if (models.isNotEmpty()) {
            item {
                BiText(Bi("Workspace (${models.size})", "工作區（${models.size}）"))
            }
            items(models) { m -> ModelRow(m) { vm.remove(m) } }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = onOpenEditor) {
                        Text(Bi("Edit slice settings", "編輯切片設定").inline)
                    }
                }
            }
            item {
                Button(onClick = { vm.slice() }, modifier = Modifier.fillMaxWidth()) {
                    Text(Bi("Slice → Preview", "切片 → 預覽").inline)
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    BiBody(Bi("No model yet — tap \"Import a model\" above to start.",
                        "未有模型 — 撳上面「匯入模型」開始。"),
                        modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: ImportedModel, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall)
                val kb = if (model.sizeBytes > 0) "${model.sizeBytes / 1024} KB" else "—"
                Text(kb, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove  移除")
            }
        }
    }
}
