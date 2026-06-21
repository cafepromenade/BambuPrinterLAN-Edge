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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.app.CommandBus
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText
import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.GcodeState

private data class CalAction(val label: Bi, val desc: Bi, val cmd: Command)

/**
 * Calibration — bed level, vibration/resonance, motor noise. Every action is
 * guarded: if a job is in progress it asks to stop the current print first.
 */
@Composable
fun CalibrationScreen(onBack: () -> Unit = {}) {
    val status by CommandBus.status.collectAsState()
    var pending by remember { mutableStateOf<CalAction?>(null) }     // waiting on the stop-job dialog
    var lastRun by remember { mutableStateOf<String?>(null) }

    val actions = listOf(
        CalAction(Bi("Bed leveling", "調平熱床"),
            Bi("Re-probe the bed mesh.", "重新量度熱床網格。"), Command.Calibrate(bedLevel = true)),
        CalAction(Bi("Vibration / resonance", "振動補償"),
            Bi("Measure input-shaping resonance.", "量度輸入整形共振。"), Command.Calibrate(vibration = true)),
        CalAction(Bi("Motor noise", "電機噪音"),
            Bi("Cancel motor noise.", "校正電機噪音。"), Command.Calibrate(motorNoise = true)),
        CalAction(Bi("Full calibration", "完整校正"),
            Bi("Bed + vibration + motor noise.", "熱床＋振動＋電機。"),
            Command.Calibrate(bedLevel = true, vibration = true, motorNoise = true)),
    )

    fun run(a: CalAction) {
        CommandBus.send(a.cmd)
        lastRun = a.label.en
    }

    fun attempt(a: CalAction) {
        if (CommandBus.printing) pending = a else run(a)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回") }
            BiText(Bi("Calibration", "校正"), enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }

        if (!CommandBus.connected) {
            BiBody(Bi("Connect a printer on the Device tab to calibrate.", "請先喺裝置頁連接打印機。"))
            return@Column
        }

        if (status?.gcodeState == GcodeState.RUNNING) {
            Card(Modifier.fillMaxWidth()) {
                BiBody(Bi("⚠ A print is running. Calibration will stop it.",
                    "⚠ 正在列印。校正會中止目前任務。"), modifier = Modifier.padding(12.dp))
            }
        }

        actions.forEach { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BiText(a.label)
                    BiBody(a.desc)
                    Button(onClick = { attempt(a) }, modifier = Modifier.fillMaxWidth()) {
                        Text(Bi("Run", "執行").inline)
                    }
                }
            }
        }

        lastRun?.let { BiBody(Bi("Started: $it", "已開始：$it")) }
    }

    // In-progress guard dialog.
    pending?.let { a ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(Bi("Stop current print?", "停止目前列印？").inline) },
            text = {
                Text(Bi(
                    "A print is in progress. Calibration can't run during a print. Stop the current job and start \"${a.label.en}\"?",
                    "目前正在列印。校正不能喺列印中進行。要停止目前任務並開始「${a.label.yue}」嗎？").inline)
            },
            confirmButton = {
                TextButton(onClick = {
                    CommandBus.send(Command.Stop)
                    run(a)
                    pending = null
                }) { Text(Bi("Stop & calibrate", "停止並校正").inline) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(Bi("Keep printing", "繼續列印").inline) }
            },
        )
    }
}
