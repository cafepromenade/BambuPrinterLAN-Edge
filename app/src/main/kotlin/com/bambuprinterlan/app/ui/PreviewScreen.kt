package com.bambuprinterlan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

@Composable
fun PreviewScreen() {
    var layer by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BiText(Bi("Preview", "預覽"), enSize = MaterialTheme.typography.headlineSmall.fontSize)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                BiText(Bi("Native engine", "原生引擎"))
                Text(com.bambuprinterlan.engine.SlicerBridge.version(),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                BiText(Bi("G-code viewer", "G-code 檢視器"))
                BiBody(Bi("Layer playback, time/flow/speed colour modes.",
                    "逐層播放，時間／流量／速度顏色模式。"))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(Bi("Layer", "層").inline + ": ${layer.toInt()}")
                Slider(value = layer, onValueChange = { layer = it }, valueRange = 0f..100f)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(Bi("Time", "時間"), "—")
                    Stat(Bi("Filament", "線材"), "—")
                    Stat(Bi("Layers", "層數"), "100")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: Bi, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label.inline, style = MaterialTheme.typography.labelSmall)
    }
}
