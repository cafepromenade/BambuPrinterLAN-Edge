package com.bambuprinterlan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.bambuprinterlan.app.DeviceViewModel
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText
import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.DeviceStatus
import com.bambuprinterlan.core.model.SpeedLevel

/**
 * Device 裝置 — connect via relay (LAN-direct MQTT lands in Phase 4), then view
 * live status and control the printer. Every label is bilingual. Secrets stay
 * on-device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceScreen(vm: DeviceViewModel = viewModel()) {
    val status by vm.status.collectAsState()
    val connected by vm.connected.collectAsState()
    val message by vm.message.collectAsState()
    val savedPrinters by vm.savedPrinters.collectAsState()
    val relayPrinters by vm.relayPrinters.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var serial by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var accessCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BiText(Bi("Device", "裝置"), enSize = MaterialTheme.typography.headlineSmall.fontSize)
        message?.let { Text(it, style = MaterialTheme.typography.labelLarge) }

        if (savedPrinters.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiText(Bi("Saved printers", "已儲存打印機"))
                    savedPrinters.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { ip = p.ip; accessCode = p.accessCode; serial = p.serial },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text((p.name.ifBlank { p.serial }) + "  ${p.ip}")
                            }
                            IconButton(onClick = { vm.removeSavedPrinter(p) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove  移除")
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiText(Bi("Connect", "連線"))
                BiBody(Bi("Easiest: scan the QR on your printer to fill everything, then tap Connect.",
                    "最簡單：掃描打印機上嘅 QR 自動填寫，再撳連線。"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        com.bambuprinterlan.app.scanQrCode(context, onResult = {
                            val p = com.bambuprinterlan.app.parsePrinterQr(it)
                            if (p.serial.isNotBlank()) serial = p.serial
                            if (p.ip.isNotBlank()) ip = p.ip
                            if (p.accessCode.isNotBlank()) accessCode = p.accessCode
                        }, onError = { /* surfaced by scanner UI */ })
                    }) { Text(Bi("Scan QR", "掃描 QR").inline) }
                }
                BiBody(Bi("Tip: on the printer, open Settings ▸ Network for the QR, IP and access code.",
                    "貼士：喺打印機開 設定 ▸ 網絡，可見 QR、IP 同存取碼。"))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(Bi("Name (optional)", "名稱（可選）").inline) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = serial, onValueChange = { serial = it },
                    label = { Text(Bi("Printer serial", "打印機序號").inline) },
                    supportingText = { Text(Bi("Code like 01P00A… on the sticker or Settings ▸ Device.",
                        "類似 01P00A… 喺機底貼紙或 設定 ▸ 裝置。").inline) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it },
                    label = { Text(Bi("Printer IP (LAN)", "打印機 IP（LAN）").inline) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = accessCode, onValueChange = { accessCode = it },
                    label = { Text(Bi("Access code (LAN)", "存取碼（LAN）").inline) },
                    supportingText = { Text(Bi("8-digit code on the printer: Settings ▸ Network ▸ LAN-only.",
                        "打印機上嘅 8 位數字碼：設定 ▸ 網絡 ▸ LAN 模式。").inline) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.connectViaLan(ip, accessCode, serial) }) {
                        Text(Bi("Connect LAN", "LAN 連線").inline)
                    }
                    Button(onClick = { vm.connectViaRelay(serial) }) {
                        Text(Bi("Relay", "中繼").inline)
                    }
                    OutlinedButton(onClick = { vm.disconnect() }) {
                        Text(Bi("Disconnect", "中斷").inline)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.savePrinterManually(name, ip, accessCode, serial) }) {
                        Text(Bi("Save printer", "儲存打印機").inline)
                    }
                    OutlinedButton(onClick = { vm.addRelayPrinter(serial, ip, accessCode, name) }) {
                        Text(Bi("Add to relay", "加入中繼").inline)
                    }
                }
                AssistChip(onClick = {}, label = {
                    Text((if (connected) Bi("Connected", "已連線") else Bi("Not connected", "未連線")).inline)
                })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BiText(Bi("Relay printers", "中繼打印機"), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.refreshRelayPrinters() }) {
                        Text(Bi("Refresh", "重新整理").inline)
                    }
                }
                if (relayPrinters.isEmpty()) {
                    BiBody(Bi("None yet — add one above, or set the relay URL in Settings.",
                        "暫時無 — 喺上面加入，或喺設定填中繼網址。"))
                }
                relayPrinters.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { serial = p.serial; vm.connectViaRelay(p.serial) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text((p.name.ifBlank { p.serial }) + (if (p.connected) "  ●" else "  ○"))
                        }
                        IconButton(onClick = { vm.removeRelayPrinter(p.serial) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove  移除")
                        }
                    }
                }
            }
        }

        if (connected) {
            StatusCard(status)
            status?.ams?.takeIf { it.units.isNotEmpty() }?.let { AmsCard(it) }
            ControlsCard(vm, status)
            SendPrintCard(vm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmsCard(ams: com.bambuprinterlan.core.model.AmsState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BiText(Bi("Filament (AMS)", "線材（AMS）"))
            ams.units.forEach { unit ->
                if (unit.humidity in 0..100) {
                    Text(Bi("Humidity", "濕度").inline + ": ${unit.humidity}",
                        style = MaterialTheme.typography.labelSmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    unit.trays.forEach { tray -> TrayChip(tray) }
                }
            }
        }
    }
}

@Composable
private fun TrayChip(tray: com.bambuprinterlan.core.model.FilamentSlot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(end = 6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(parseTrayColor(tray.colorHex))
        )
        Column {
            Text(tray.type.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
            val remain = if (tray.remainPercent in 0..100) "${tray.remainPercent}%" else "—"
            Text(remain, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Bambu tray_color is RRGGBBAA hex; use the RGB part. */
private fun parseTrayColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    if (clean.length < 6) return Color.Gray
    return runCatching {
        Color(android.graphics.Color.parseColor("#" + clean.substring(0, 6)))
    }.getOrDefault(Color.Gray)
}

@Composable
private fun SendPrintCard(vm: DeviceViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.sendPrint(it) } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BiText(Bi("Send print", "傳送列印"))
            BiBody(Bi("Upload a sliced .3mf and start it.", "上載切片好嘅 .3mf 並開始。"))
            Button(onClick = { picker.launch(arrayOf("application/vnd.ms-3mfdocument", "model/3mf", "*/*")) }) {
                Text(Bi("Choose .3mf & print", "揀 .3mf 並列印").inline)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(status: DeviceStatus?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BiText(Bi("Status", "狀態"))
            val s = status
            if (s == null) {
                BiBody(Bi("Waiting for first report…", "等緊第一份狀態回報…"))
            } else {
                Text(Bi("State", "狀態").inline + ": ${s.gcodeState}")
                LinearProgressIndicator(
                    progress = { s.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(Bi("Progress", "進度").inline + ": ${s.progressPercent}%  ·  " +
                    Bi("Layer", "層").inline + " ${s.layerNum}/${s.totalLayerNum}")
                Text(Bi("Remaining", "剩餘").inline + ": ${s.remainingMinutes} " + Bi("min", "分鐘").inline)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Temp(Bi("Nozzle", "噴嘴"), s.nozzleTemper, s.nozzleTargetTemper)
                    Temp(Bi("Bed", "熱床"), s.bedTemper, s.bedTargetTemper)
                    Temp(Bi("Chamber", "機艙"), s.chamberTemper, 0f)
                }
                if (s.hms.isNotEmpty()) HmsAlerts(s.hms)
            }
        }
    }
}

@Composable
private fun HmsAlerts(alerts: List<com.bambuprinterlan.core.model.HmsAlert>) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BiText(Bi("Alerts (${alerts.size})", "警報（${alerts.size}）"))
        alerts.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${a.severity}  ${a.wikiCode}", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { runCatching { uriHandler.openUri(a.wikiUrl) } }) {
                    Text(Bi("Wiki", "說明").inline)
                }
            }
        }
    }
}

@Composable
private fun Temp(label: Bi, current: Float, target: Float) {
    Column {
        Text("${current.toInt()}°" + if (target > 0) " / ${target.toInt()}°" else "",
            style = MaterialTheme.typography.titleMedium)
        Text(label.inline, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsCard(vm: DeviceViewModel, status: DeviceStatus?) {
    var showStopConfirm by remember { mutableStateOf(false) }
    var nozzleTarget by remember { mutableStateOf("") }
    var bedTarget by remember { mutableStateOf("") }
    var partFan by remember { mutableFloatStateOf((status?.coolingFanSpeed ?: 0).toFloat()) }
    var auxFan by remember { mutableFloatStateOf(0f) }
    var chamberFan by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var step by remember { mutableFloatStateOf(10f) }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(Bi("Stop print?", "停止列印？").inline) },
            text = { Text(Bi("This cancels the current print and cannot be undone.",
                "會取消目前列印，無法復原。").inline) },
            confirmButton = {
                TextButton(onClick = { showStopConfirm = false; vm.send(Command.Stop) }) {
                    Text(Bi("Stop", "停止").inline)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text(Bi("Cancel", "取消").inline)
                }
            },
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BiText(Bi("Controls", "控制"))

            // Print job
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.send(Command.Pause) }) { Text(Bi("Pause", "暫停").inline) }
                FilledTonalButton(onClick = { vm.send(Command.Resume) }) { Text(Bi("Resume", "繼續").inline) }
                FilledTonalButton(onClick = { showStopConfirm = true }) { Text(Bi("Stop", "停止").inline) }
                val lightOn = status?.chamberLightOn ?: false
                FilledTonalButton(onClick = { vm.send(Command.ChamberLight(!lightOn)) }) {
                    Text((if (lightOn) Bi("Light off", "關燈") else Bi("Light on", "開燈")).inline)
                }
                FilledTonalButton(onClick = { vm.send(Command.StopBuzzer) }) { Text(Bi("Mute buzzer", "靜音").inline) }
            }

            // Print speed
            Text(Bi("Print speed", "列印速度").inline, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SpeedLevel.SILENT to Bi("Silent", "靜音"),
                    SpeedLevel.STANDARD to Bi("Standard", "標準"),
                    SpeedLevel.SPORT to Bi("Sport", "運動"),
                    SpeedLevel.LUDICROUS to Bi("Ludicrous", "瘋狂"),
                ).forEach { (level, label) ->
                    FilledTonalButton(onClick = { vm.send(Command.SetSpeed(level)) }) {
                        Text((if (status?.speedLevel == level) "● " else "") + label.inline)
                    }
                }
            }

            // Motion
            Text(Bi("Move (mm)", "移動（毫米）").inline + ": ${step.toInt()}",
                style = MaterialTheme.typography.labelLarge)
            Slider(value = step, onValueChange = { step = it }, valueRange = 1f..50f)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.send(Command.HomeAll) }) { Text(Bi("Home", "歸位").inline) }
                FilledTonalButton(onClick = { vm.send(Command.Move("X", -step)) }) { Text("X-") }
                FilledTonalButton(onClick = { vm.send(Command.Move("X", step)) }) { Text("X+") }
                FilledTonalButton(onClick = { vm.send(Command.Move("Y", -step)) }) { Text("Y-") }
                FilledTonalButton(onClick = { vm.send(Command.Move("Y", step)) }) { Text("Y+") }
                FilledTonalButton(onClick = { vm.send(Command.Move("Z", -step)) }) { Text("Z-") }
                FilledTonalButton(onClick = { vm.send(Command.Move("Z", step)) }) { Text("Z+") }
                FilledTonalButton(onClick = { vm.send(Command.Extrude(step)) }) { Text(Bi("Extrude", "出料").inline) }
                FilledTonalButton(onClick = { vm.send(Command.Extrude(-step)) }) { Text(Bi("Retract", "回抽").inline) }
            }

            // Temperature
            Text(Bi("Temperature", "溫度").inline, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(nozzleTarget, { nozzleTarget = it.filter(Char::isDigit) },
                    label = { Text(Bi("Nozzle °C", "噴嘴 °C").inline) }, singleLine = true,
                    modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { nozzleTarget.toIntOrNull()?.let { vm.send(Command.SetNozzleTemp(it)) } }) {
                    Text(Bi("Set", "設定").inline)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(bedTarget, { bedTarget = it.filter(Char::isDigit) },
                    label = { Text(Bi("Bed °C", "熱床 °C").inline) }, singleLine = true,
                    modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { bedTarget.toIntOrNull()?.let { vm.send(Command.SetBedTemp(it)) } }) {
                    Text(Bi("Set", "設定").inline)
                }
            }
            OutlinedButton(onClick = { vm.send(Command.Cooldown) }) { Text(Bi("Cooldown", "降溫").inline) }

            // Fans
            Text(Bi("Part fan", "部件風扇").inline + ": ${partFan.toInt()}%",
                style = MaterialTheme.typography.labelLarge)
            Slider(value = partFan, onValueChange = { partFan = it },
                onValueChangeFinished = { vm.send(Command.PartFan(partFan.toInt())) }, valueRange = 0f..100f)
            Text(Bi("Aux fan", "輔助風扇").inline + ": ${auxFan.toInt()}%",
                style = MaterialTheme.typography.labelLarge)
            Slider(value = auxFan, onValueChange = { auxFan = it },
                onValueChangeFinished = { vm.send(Command.AuxFan(auxFan.toInt())) }, valueRange = 0f..100f)
            Text(Bi("Chamber fan", "機箱風扇").inline + ": ${chamberFan.toInt()}%",
                style = MaterialTheme.typography.labelLarge)
            Slider(value = chamberFan, onValueChange = { chamberFan = it },
                onValueChangeFinished = { vm.send(Command.ChamberFan(chamberFan.toInt())) }, valueRange = 0f..100f)

            // Camera / timelapse
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Bi("Timelapse recording", "縮時錄影").inline,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = recording, onCheckedChange = {
                    recording = it; vm.send(Command.Record(it))
                })
            }

            // Calibration
            Text(Bi("Calibration", "校準").inline, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.send(Command.Calibrate(bedLevel = true)) }) { Text(Bi("Bed level", "調平").inline) }
                OutlinedButton(onClick = { vm.send(Command.Calibrate(vibration = true)) }) { Text(Bi("Vibration", "振動").inline) }
                OutlinedButton(onClick = { vm.send(Command.Calibrate(motorNoise = true)) }) { Text(Bi("Motor", "馬達").inline) }
            }

            // AMS
            if (status?.ams?.units?.isNotEmpty() == true) {
                Text(Bi("AMS", "AMS").inline, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { tray ->
                        OutlinedButton(onClick = { vm.send(Command.AmsSelectTray(tray)) }) {
                            Text(Bi("Tray ${tray + 1}", "料盤 ${tray + 1}").inline)
                        }
                    }
                    OutlinedButton(onClick = { vm.send(Command.AmsControl("resume")) }) { Text(Bi("Resume AMS", "繼續").inline) }
                }
            }
        }
    }
}
