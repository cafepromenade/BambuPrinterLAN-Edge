package com.bambuprinterlan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.app.AmsMappingStore
import com.bambuprinterlan.app.CommandBus
import com.bambuprinterlan.app.FilamentProfile
import com.bambuprinterlan.app.FilamentProfiles
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText
import com.bambuprinterlan.core.model.AmsUnit
import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.FilamentSlot

/**
 * Filament / AMS management — load & unload, assign filament type + colour per
 * tray from a profile library, and pick which tray feeds the next print.
 */
@Composable
fun FilamentScreen(onBack: () -> Unit = {}) {
    val status by CommandBus.status.collectAsState()
    val mapping by AmsMappingStore.mapping.collectAsState()
    var editing by remember { mutableStateOf<Pair<Int, FilamentSlot>?>(null) }  // amsId, tray

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回") }
            BiText(Bi("Filament / AMS", "線材 / AMS"),
                enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }
        BiBody(Bi("AMS = Automatic Material System, the spool changer. Lower humidity is better (under ~20% keeps filament dry).",
            "AMS = 自動換料系統（換卷器）。濕度越低越好（低於約 20% 可保持乾燥）。"))

        if (!CommandBus.connected) {
            BiBody(Bi("Connect a printer on the Device tab to manage filament.",
                "請先喺裝置頁連接打印機先可管理線材。"))
            return@Column
        }

        val ams = status?.ams
        if (ams == null || ams.units.isEmpty()) {
            BiBody(Bi("No AMS detected. External-spool printers load filament directly.",
                "未偵測到 AMS。外置料盤打印機可直接入料。"))
        }

        ams?.units?.forEach { unit ->
            AmsUnitCard(unit, mapping.firstOrNull(),
                onLoad = { tray -> CommandBus.send(Command.AmsLoad(tray.id, tray.nozzleTempMin.coerceAtLeast(200), tray.nozzleTempMax.coerceAtLeast(220))) },
                onUnload = { CommandBus.send(Command.AmsUnload) },
                onEdit = { tray -> editing = unit.id to tray },
                onMap = { tray -> AmsMappingStore.setSingleTray(tray.id) },
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiText(Bi("AMS actions", "AMS 操作"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { CommandBus.send(Command.AmsControl("resume")) }) {
                        Text(Bi("Resume", "繼續").inline)
                    }
                    OutlinedButton(onClick = { CommandBus.send(Command.AmsControl("reset")) }) {
                        Text(Bi("Reset", "重設").inline)
                    }
                    OutlinedButton(onClick = { AmsMappingStore.clear() }) {
                        Text(Bi("Auto map", "自動配對").inline)
                    }
                }
                BiBody(Bi(
                    "Next print uses tray " + (mapping.firstOrNull()?.plus(1)?.toString() ?: "(auto)") + ".",
                    "下次列印使用料盤 " + (mapping.firstOrNull()?.plus(1)?.toString() ?: "（自動）") + "。"))
            }
        }
    }

    editing?.let { (amsId, tray) ->
        FilamentPickerDialog(
            onDismiss = { editing = null },
            onPick = { p ->
                CommandBus.send(Command.SetTrayFilament(
                    amsId = amsId, trayId = tray.id, trayType = p.type,
                    colorHex = tray.colorHex.ifBlank { p.defaultColor },
                    nozzleTempMin = p.nozzleMin, nozzleTempMax = p.nozzleMax, trayInfoIdx = p.infoIdx,
                ))
                editing = null
            },
        )
    }
}

@Composable
private fun AmsUnitCard(
    unit: AmsUnit,
    mappedTray: Int?,
    onLoad: (FilamentSlot) -> Unit,
    onUnload: () -> Unit,
    onEdit: (FilamentSlot) -> Unit,
    onMap: (FilamentSlot) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val h = if (unit.humidity in 0..100) "${unit.humidity}" else "—"
            BiText(Bi("AMS ${unit.id + 1} · humidity $h", "AMS ${unit.id + 1} · 濕度 $h"))
            unit.trays.forEach { tray ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(parseColor(tray.colorHex)))
                        val remain = if (tray.remainPercent in 0..100) "${tray.remainPercent}%" else "—"
                        Text("${tray.id + 1}", style = MaterialTheme.typography.labelLarge)
                        Text(tray.type.ifBlank { "—" } + "  ·  " + remain,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        if (mappedTray == tray.id) Text("➜", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onLoad(tray) }) { Text(Bi("Load", "入料").inline) }
                        OutlinedButton(onClick = onUnload) { Text(Bi("Unload", "退料").inline) }
                        OutlinedButton(onClick = { onEdit(tray) }) { Text(Bi("Edit", "編輯").inline) }
                        OutlinedButton(onClick = { onMap(tray) }) { Text(Bi("Use", "使用").inline) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilamentPickerDialog(onDismiss: () -> Unit, onPick: (FilamentProfile) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(Bi("Cancel", "取消").inline) } },
        title = { Text(Bi("Choose filament", "選擇線材").inline) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilamentProfiles.all.forEach { p ->
                    TextButton(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(parseColor(p.defaultColor)))
                            Text(p.name + "  (${p.nozzleMin}–${p.nozzleMax}°)",
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    )
}

/** RRGGBBAA hex → Color (RGB part). */
private fun parseColor(hex: String): Color = runCatching {
    val h = hex.removePrefix("#").padEnd(6, '0')
    Color(0xFF000000 or (h.substring(0, 6).toLong(16)))
}.getOrDefault(Color(0xFF888888))
