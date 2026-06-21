package com.bambuprinterlan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.app.ModelEditStore
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * Model Edit Lab — transform (scale/rotate/move/center) and slice settings
 * (layer height, infill, walls, temps) fed to the native slicer. Bilingual.
 */
@Composable
fun ModelEditScreen(onBack: () -> Unit = {}) {
    val s by ModelEditStore.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回") }
            BiText(Bi("Model Edit Lab", "模型編輯實驗室"),
                enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }
        BiBody(Bi("Applied when you slice in Prepare.", "喺準備頁切片時套用。"))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiText(Bi("Transform", "變形"))
                Labeled(Bi("Scale", "縮放"), "${(s.scale * 100).toInt()}%")
                Slider(s.scale, { v -> ModelEditStore.update { it.copy(scale = v) } }, valueRange = 0.25f..3f)
                Labeled(Bi("Rotate Z", "旋轉 Z"), "${s.rotateZ.toInt()}°")
                Slider(s.rotateZ, { v -> ModelEditStore.update { it.copy(rotateZ = v) } }, valueRange = 0f..360f)
                Labeled(Bi("Move X", "移動 X"), "${s.moveX.toInt()} mm")
                Slider(s.moveX, { v -> ModelEditStore.update { it.copy(moveX = v) } }, valueRange = -100f..100f)
                Labeled(Bi("Move Y", "移動 Y"), "${s.moveY.toInt()} mm")
                Slider(s.moveY, { v -> ModelEditStore.update { it.copy(moveY = v) } }, valueRange = -100f..100f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Bi("Center on plate", "置中於打印板").inline, modifier = Modifier.weight(1f))
                    Switch(s.center, { v -> ModelEditStore.update { it.copy(center = v) } })
                }
                OutlinedButton(onClick = { ModelEditStore.update {
                    it.copy(scale = 1f, rotateZ = 0f, moveX = 0f, moveY = 0f, center = true)
                } }) { Text(Bi("Reset transform", "重設變形").inline) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiText(Bi("Slice settings", "切片設定"))
                Labeled(Bi("Layer height", "層高"), "${s.layerHeight} mm")
                Slider(s.layerHeight, { v ->
                    ModelEditStore.update { it.copy(layerHeight = (Math.round(v * 100) / 100f)) }
                }, valueRange = 0.08f..0.32f)
                Labeled(Bi("Infill", "填充"), "${s.infill}%")
                Slider(s.infill.toFloat(), { v -> ModelEditStore.update { it.copy(infill = v.toInt()) } },
                    valueRange = 0f..100f)
                Labeled(Bi("Walls", "牆數"), "${s.walls}")
                Slider(s.walls.toFloat(), { v -> ModelEditStore.update { it.copy(walls = v.toInt().coerceIn(1, 5)) } },
                    valueRange = 1f..5f)
                Labeled(Bi("Brim", "裙邊"), "${s.brim}")
                Slider(s.brim.toFloat(), { v -> ModelEditStore.update { it.copy(brim = v.toInt().coerceIn(0, 10)) } },
                    valueRange = 0f..10f)
                Labeled(Bi("Nozzle °C", "噴嘴 °C"), "${s.nozzleTemp}")
                Slider(s.nozzleTemp.toFloat(), { v -> ModelEditStore.update { it.copy(nozzleTemp = v.toInt()) } },
                    valueRange = 170f..300f)
                Labeled(Bi("Bed °C", "熱床 °C"), "${s.bedTemp}")
                Slider(s.bedTemp.toFloat(), { v -> ModelEditStore.update { it.copy(bedTemp = v.toInt()) } },
                    valueRange = 0f..120f)
            }
        }
    }
}

@Composable
private fun Labeled(label: Bi, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label.inline, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
