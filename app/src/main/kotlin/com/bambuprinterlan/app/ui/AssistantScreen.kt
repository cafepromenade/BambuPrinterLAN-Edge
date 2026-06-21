package com.bambuprinterlan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.bambuprinterlan.app.AssistantMode
import com.bambuprinterlan.app.AssistantViewModel
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * AI labs — Feature Suggestion / AI Filament / Sidechat / Community Miner / Ask,
 * all backed by the on-device Anthropic API key (entered in Settings). Bilingual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(vm: AssistantViewModel = viewModel(), onBack: () -> Unit = {}) {
    val output by vm.output.collectAsState()
    val busy by vm.busy.collectAsState()
    var mode by remember { mutableStateOf(AssistantMode.FEATURE) }
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回")
            }
            BiText(Bi("AI Labs", "AI 實驗室"), enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }
        BiBody(Bi("Uses your Anthropic API key from Settings.", "用設定入面嘅 Anthropic API key。"))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantMode.entries.forEach { m ->
                FilterChip(selected = mode == m, onClick = { mode = m },
                    label = { Text(m.title.inline) })
            }
        }

        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text(mode.hint.inline) },
            modifier = Modifier.fillMaxWidth(), minLines = 3,
        )
        Button(onClick = { vm.run(mode, input) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text((if (busy) Bi("Running…", "執行緊…") else Bi("Run", "執行")).inline)
        }

        if (output.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    BiText(Bi("Result", "結果"))
                    Text(output, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
