package com.bambuprinterlan.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.core.design.Bi

private data class Step(val title: Bi, val body: Bi, val emoji: String)

private val STEPS = listOf(
    Step(Bi("Welcome to Bambu Printer LAN", "歡迎使用 Bambu Printer LAN"),
        Bi("Slice 3D models and control your Bambu printer over your home Wi-Fi. Everything is shown in English and Cantonese.",
            "喺屋企 Wi-Fi 切片 3D 模型同操控 Bambu 打印機。所有文字都有中英對照。"), "👋"),
    Step(Bi("1 · Import & Slice", "1 · 匯入同切片"),
        Bi("On the Prepare tab, tap \"Import a model\" (STL/OBJ/3MF), then \"Slice\". You can tune layers, infill and supports first.",
            "喺準備頁撳「匯入模型」（STL/OBJ/3MF），再撳「切片」。可先調層高、填充等。"), "🧩"),
    Step(Bi("2 · Connect your printer", "2 · 連接打印機"),
        Bi("On the Device tab, scan the QR on your printer (Settings ▸ Network) to fill the IP and access code, then Connect.",
            "喺裝置頁掃描打印機上嘅 QR（設定 ▸ 網絡）自動填 IP 同存取碼，再連線。"), "🖨️"),
    Step(Bi("3 · Print & monitor", "3 · 列印同監察"),
        Bi("Send the sliced file to print and watch live progress — with an ongoing notification you can pause or stop from.",
            "將切片檔傳去列印，實時睇進度，仲可喺通知度暫停或停止。"), "✅"),
)

/** One-time first-run guide. Persists a "seen" flag so it shows only once. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val s = STEPS[step]
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(s.emoji, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.size(20.dp))
            Text(s.title.inline, style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center)
            Spacer(Modifier.size(12.dp))
            Text(s.body.inline, style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center)
            Spacer(Modifier.size(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STEPS.indices.forEach { i ->
                    Box(Modifier.size(if (i == step) 11.dp else 8.dp).clip(CircleShape)
                        .background(if (i == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Spacer(Modifier.size(28.dp))
            Button(onClick = { if (step < STEPS.lastIndex) step++ else onDone() },
                modifier = Modifier.fillMaxWidth()) {
                Text((if (step < STEPS.lastIndex) Bi("Next", "下一步") else Bi("Start", "開始")).inline)
            }
            TextButton(onClick = onDone) { Text(Bi("Skip", "略過").inline) }
        }
    }
}

object Onboarding {
    private const val PREF = "onboarding"
    private const val KEY = "seen_v1"
    fun shouldShow(ctx: Context): Boolean =
        !ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun markSeen(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
}
