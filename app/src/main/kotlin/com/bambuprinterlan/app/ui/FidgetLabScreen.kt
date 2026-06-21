package com.bambuprinterlan.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiText
import kotlinx.coroutines.delay

/**
 * Material Fidget Lab — port of BambuLan's animated Material control playground
 * (MainFrame.cpp MaterialFidgetDialog). Every control is touchable and the
 * progress bar auto-animates. Bilingual throughout.
 */
@Composable
fun FidgetLabScreen(onBack: () -> Unit = {}) {
    var progress by remember { mutableFloatStateOf(0.42f) }
    var auto by remember { mutableStateOf(true) }
    var switchOn by remember { mutableStateOf(true) }
    var chip by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf(Bi("Everything here is touchable.", "呢度全部都撳得。")) }

    LaunchedEffect(auto) {
        var dir = 1f
        while (auto) {
            progress += dir * 0.015f
            if (progress >= 1f) { progress = 1f; dir = -1f }
            if (progress <= 0f) { progress = 0f; dir = 1f }
            delay(40)
        }
    }
    val animated by animateFloatAsState(progress, label = "fidget")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回")
                }
                BiText(Bi("Material Fidget Lab", "Material 玩具實驗室"),
                    enSize = MaterialTheme.typography.headlineSmall.fontSize)
            }
        }
        item { BiText(status) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiText(Bi("Buttons", "按鈕"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { status = Bi("Filled pressed", "撳咗 Filled") }) { Text(Bi("Filled", "實心").inline) }
                        FilledTonalButton(onClick = { status = Bi("Tonal pressed", "撳咗 Tonal") }) { Text(Bi("Tonal", "色調").inline) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { status = Bi("Outlined pressed", "撳咗 Outlined") }) { Text(Bi("Outlined", "外框").inline) }
                        TextButton(onClick = { status = Bi("Text pressed", "撳咗 Text") }) { Text(Bi("Text", "文字").inline) }
                        ElevatedButton(onClick = { status = Bi("Elevated pressed", "撳咗 Elevated") }) { Text(Bi("Elevated", "浮起").inline) }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiText(Bi("Inputs", "輸入"))
                    Text(Bi("Slider sets progress", "滑桿設定進度").inline)
                    Slider(value = progress, onValueChange = { progress = it; auto = false })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Bi("Animate", "自動郁").inline, modifier = Modifier.padding(end = 8.dp))
                        Switch(checked = auto, onCheckedChange = { auto = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Bi("Calm", "平靜"), Bi("Bouncy", "彈跳"), Bi("Zippy", "快速")).forEachIndexed { i, label ->
                            FilterChip(selected = chip == i, onClick = { chip = i },
                                label = { Text(label.inline) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Bi("Switch", "開關").inline, modifier = Modifier.padding(end = 8.dp))
                        Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiText(Bi("Progress", "進度"))
                    LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                    Text("${(animated * 100).toInt()}%")
                }
            }
        }
    }
}
