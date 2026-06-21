package com.bambuprinterlan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambuprinterlan.core.data.SettingsRepository
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Material Command Bar with a configurable live Clock — port of BambuLan's
 * command bar + Material Clock (material_clock_* keys: color/seconds/date/prefix).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandBar() {
    CenterAlignedTopAppBar(
        title = { Text("Bambu Printer LAN") },
        actions = { MaterialClock(Modifier.padding(end = 14.dp)) },
    )
}

@Composable
fun MaterialClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val showSeconds by repo.getBool("material_clock_show_seconds").collectAsState(initial = false)
    val showDate by repo.getBool("material_clock_show_date").collectAsState(initial = false)
    val prefix by repo.getString("material_clock_prefix").collectAsState(initial = "")
    val colorHex by repo.getString("material_clock_color").collectAsState(initial = "#1A73E8")
    val textSize by repo.getInt("material_clock_text_size").collectAsState(initial = 18)

    val text by produceState("", showSeconds, showDate, prefix) {
        while (true) {
            val pattern = (if (showDate) "yyyy-MM-dd " else "") + (if (showSeconds) "HH:mm:ss" else "HH:mm")
            val now = SimpleDateFormat(pattern, Locale.getDefault()).format(Calendar.getInstance().time)
            value = if (prefix.isNotBlank()) "$prefix $now" else now
            delay(1000)
        }
    }
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color(0xFF1A73E8))
    Text(
        text, color = color, modifier = modifier,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = textSize.coerceIn(12, 34).sp),
    )
}

/** Startup intro transition — port of the 4-step Material intro splash. */
@Composable
fun StartupIntro(onDone: () -> Unit) {
    val steps = listOf(
        Bi("Starting BambuPrinterLan", "啟動 BambuPrinterLan"),
        Bi("Loading materials", "載入物料"),
        Bi("Warming up", "預熱緊"),
        Bi("Ready", "準備好喇"),
    )
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffectOnce {
        for (i in steps.indices) { idx = i; delay(420) }
        delay(180)
        onDone()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bambu Printer LAN", style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary)
        BiText(steps[idx], modifier = Modifier.padding(top = 12.dp))
    }
}

/** 1% chance haha dialog on startup — port of the bambulan_haha_dialog flag. */
@Composable
fun HahaGate() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val enabled by repo.isFeatureEnabled("bambulan_haha_dialog").collectAsState(initial = true)
    var show by remember { mutableStateOf(false) }
    LaunchedEffectOnce {
        if (enabled && Math.random() < 0.01) show = true
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false }) { Text("OK") } },
            title = { Text("haha") },
            text = { Text("haha") },
        )
    }
}

@Composable
private fun LaunchedEffectOnce(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
