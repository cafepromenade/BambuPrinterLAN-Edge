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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiText

/**
 * Material File Hub — port of BambuLan's custom File Hub (MainFrame.cpp:2894),
 * the bilingual entry point for import, store, model library, and settings
 * bundle. Web entries open in the browser.
 */
private data class HubEntry(
    val icon: ImageVector,
    val title: Bi,
    val detail: Bi,
    val url: String? = null,
    val action: HubAction? = null,
)

private enum class HubAction { IMPORT, SETTINGS }

@Composable
fun FileHubScreen(
    onBack: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val entries = listOf(
        HubEntry(Icons.Outlined.FileOpen, Bi("Import model", "匯入模型"),
            Bi("STL · OBJ · 3MF · STEP · AMF", "STL · OBJ · 3MF · STEP · AMF"), action = HubAction.IMPORT),
        HubEntry(Icons.Outlined.Inventory2, Bi("Bambu Store", "Bambu 商店"),
            Bi("Open the Bambu store.", "開啟 Bambu 商店。"), url = "https://bambulab.com/"),
        HubEntry(Icons.Outlined.Public, Bi("MakerWorld", "MakerWorld"),
            Bi("Browse the model library.", "瀏覽模型庫。"), url = "https://makerworld.com/"),
        HubEntry(Icons.Outlined.CloudDownload, Bi("Wiki & support", "Wiki 同支援"),
            Bi("Troubleshooting and guides.", "疑難排解同指南。"), url = "https://wiki.bambulab.com/"),
        HubEntry(Icons.Outlined.Settings, Bi("Settings & .bambulan bundle", "設定同 .bambulan 套件"),
            Bi("Config + import/export bundle.", "設定加匯入匯出套件。"), action = HubAction.SETTINGS),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回")
                }
                BiText(Bi("File Hub", "檔案中心"),
                    enSize = MaterialTheme.typography.headlineSmall.fontSize)
            }
        }
        items(entries) { e ->
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(e.icon, contentDescription = e.title.en, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        BiText(e.title)
                        Text(e.detail.en, style = MaterialTheme.typography.bodyMedium)
                        Text(e.detail.yue, style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Whole-card tap target handled below via clickable row buttons
                Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        when {
                            e.url != null -> runCatching { uriHandler.openUri(e.url) }
                            e.action == HubAction.IMPORT -> onImport()
                            e.action == HubAction.SETTINGS -> onSettings()
                        }
                    }) {
                        Text(Bi("Open", "開啟").inline)
                    }
                }
            }
        }
    }
}
