package com.bambuprinterlan.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.bambuprinterlan.app.SliceStore
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

@Composable
fun PreviewScreen(onOpenDevice: () -> Unit = {}) {
    val slice by SliceStore.result.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BiText(Bi("Preview", "預覽"), enSize = MaterialTheme.typography.headlineSmall.fontSize)

        val s = slice
        if (s == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    BiText(Bi("No slice yet", "未有切片"))
                    BiBody(Bi("Import a model in Prepare and tap Slice.",
                        "喺準備頁匯入模型，然後撳切片。"))
                    Text(com.bambuprinterlan.engine.SlicerBridge.version(),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    BiText(Bi("Sliced: ${s.modelName}", "已切片：${s.modelName}"))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Stat(Bi("Layers", "層數"), s.layers.toString())
                        Stat(Bi("G-code", "G-code"), "${s.bytes / 1024} KB")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    BiText(Bi("G-code (head)", "G-code（開頭）"))
                    Text(
                        s.gcodeHead, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    )
                }
            }
            Button(onClick = onOpenDevice, modifier = Modifier.fillMaxWidth()) {
                Text(Bi("Print on device →", "喺裝置列印 →").inline)
            }
            BiBody(Bi("Sends you to the Device tab to upload + start the print.",
                "帶你去裝置頁上載並開始列印。"))
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
