package com.bambuprinterlan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bambuprinterlan.app.FeatureCatalog
import com.bambuprinterlan.app.FeatureFlag
import com.bambuprinterlan.app.SettingsViewModel
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiText

/**
 * Tools / Labs — the BambuLan custom features, gated by the same flag keys
 * (`bambulan_*`, `discord_*`, etc.) as the desktop app. Toggling mirrors
 * BambuLan Feature Settings and persists via DataStore (:core:data).
 */
@Composable
fun ToolsScreen(
    vm: SettingsViewModel = viewModel(),
    onOpenBatch: () -> Unit = {},
    onOpenFidget: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenAutoFix: () -> Unit = {},
    onOpenModelEdit: () -> Unit = {},
    onOpenFilament: () -> Unit = {},
    onOpenCalibration: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    val toggles by vm.flags.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BiText(Bi("Tools & Labs", "工具同實驗室"),
                enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }
        item {
            androidx.compose.material3.Button(onClick = onOpenBatch, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Batch Printer Sender", "批次列印傳送").inline)
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(onClick = onOpenFidget, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Material Fidget Lab", "Material 玩具實驗室").inline)
            }
        }
        item {
            androidx.compose.material3.Button(onClick = onOpenAssistant, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("AI Labs", "AI 實驗室").inline)
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(onClick = onOpenAutoFix, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Auto-Fix Dashboard", "自動修復面板").inline)
            }
        }
        item {
            androidx.compose.material3.Button(onClick = onOpenFilament, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Filament / AMS", "線材 / AMS").inline)
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Calibration", "校正").inline)
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Print history", "列印紀錄").inline)
            }
        }
        item {
            androidx.compose.material3.Button(onClick = onOpenModelEdit, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Model Edit Lab (3D)", "模型編輯（3D）").inline)
            }
        }
        item {
            Text(
                Bi("${FeatureCatalog.flags.size} BambuLan features",
                    "${FeatureCatalog.flags.size} 個 BambuLan 功能").inline,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items(FeatureCatalog.flags) { flag ->
            FeatureRow(flag, toggles[flag.key] ?: flag.defaultOn) { vm.setFeature(flag.key, it) }
        }
    }
}

@Composable
private fun FeatureRow(flag: FeatureFlag, on: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${flag.title.en}, phase ${flag.phase}" }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BiText(flag.title)
                Text(flag.detail.en, style = MaterialTheme.typography.bodyMedium)
                Text(flag.detail.yue, style = MaterialTheme.typography.bodySmall)
                AssistChip(
                    onClick = {},
                    label = { Text(Bi("Phase ${flag.phase}", "階段 ${flag.phase}").inline) },
                )
            }
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}
