package com.bambuprinterlan.app.ui

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.bambuprinterlan.app.SettingsViewModel
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * Settings 設定 — relay, Home Assistant, Discord, and auto-save config.
 * Every label is bilingual (Cantonese + English). Secrets stay on-device.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsState()
    val toast by vm.toast.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BiText(Bi("Settings", "設定"), enSize = MaterialTheme.typography.headlineSmall.fontSize)
        toast?.let { Text(it, style = MaterialTheme.typography.labelLarge) }

        Section(Bi("Relay", "中繼"), Bi("Optional remote-access server.", "可選嘅遠端連線伺服器。")) {
            Field(Bi("Relay URL", "中繼網址"), s.relayUrl) { v -> vm.update { it.copy(relayUrl = v) } }
            Field(Bi("Relay token", "中繼權杖"), s.relayToken, secret = true) { v ->
                vm.update { it.copy(relayToken = v) }
            }
        }

        Section(Bi("Home Assistant", "Home Assistant"),
            Bi("Printer-event home automation.", "打印機事件家居自動化。")) {
            ToggleRow(Bi("Enable Home Assistant", "啟用 Home Assistant"), s.haEnabled) { v ->
                vm.update { it.copy(haEnabled = v) }
            }
            Field(Bi("Base URL", "基底網址"), s.haUrl) { v -> vm.update { it.copy(haUrl = v) } }
            Field(Bi("Long-lived token", "長期權杖"), s.haToken, secret = true) { v ->
                vm.update { it.copy(haToken = v) }
            }
            Text(Bi("Allowed controls", "允許控制").inline, style = MaterialTheme.typography.labelMedium)
            ToggleRow(Bi("Lights", "燈光"), s.haAllow["lights"] ?: true) { vm.setHaDomain("lights", it) }
            ToggleRow(Bi("Scenes", "場景"), s.haAllow["scenes"] ?: true) { vm.setHaDomain("scenes", it) }
            ToggleRow(Bi("Switches", "開關"), s.haAllow["switches"] ?: false) { vm.setHaDomain("switches", it) }
            ToggleRow(Bi("Climate", "冷暖氣"), s.haAllow["climate"] ?: false) { vm.setHaDomain("climate", it) }
            OutlinedButton(onClick = { vm.testHomeAssistant() }) {
                Text(Bi("Test connection", "測試連線").inline)
            }
        }

        Section(Bi("Discord", "Discord"),
            Bi("Webhook notifications for important events.", "重要事件嘅 Webhook 通知。")) {
            ToggleRow(Bi("Enable Discord webhooks", "啟用 Discord Webhook"), s.discordEnabled) { v ->
                vm.update { it.copy(discordEnabled = v) }
            }
            Field(Bi("Webhook URL", "Webhook 網址"), s.discordUrl, secret = true) { v ->
                vm.update { it.copy(discordUrl = v) }
            }
            Text(Bi("Notify on", "通知事件").inline, style = MaterialTheme.typography.labelMedium)
            ToggleRow(Bi("Startup", "啟動"), s.discordNotify["startup"] ?: true) { vm.setDiscordCategory("startup", it) }
            ToggleRow(Bi("Project", "專案"), s.discordNotify["project"] ?: true) { vm.setDiscordCategory("project", it) }
            ToggleRow(Bi("Export", "匯出"), s.discordNotify["export"] ?: true) { vm.setDiscordCategory("export", it) }
            ToggleRow(Bi("Auto-fix", "自動修復"), s.discordNotify["autofix"] ?: true) { vm.setDiscordCategory("autofix", it) }
            ToggleRow(Bi("Errors", "錯誤"), s.discordNotify["errors"] ?: true) { vm.setDiscordCategory("errors", it) }
            OutlinedButton(onClick = { vm.testDiscord() }) {
                Text(Bi("Send test", "傳送測試").inline)
            }
        }

        Section(Bi("AI Labs", "AI 實驗室"),
            Bi("Anthropic API key for the AI labs (stored on-device).",
                "AI 實驗室用嘅 Anthropic API key（存喺裝置）。")) {
            Field(Bi("Anthropic API key", "Anthropic API key"), s.anthropicApiKey, secret = true) { v ->
                vm.update { it.copy(anthropicApiKey = v) }
            }
        }

        Section(Bi("Clock", "時鐘"), Bi("Command-bar clock display.", "指令列時鐘顯示。")) {
            ToggleRow(Bi("Show seconds", "顯示秒數"), s.clockSeconds) { v ->
                vm.update { it.copy(clockSeconds = v) }
            }
            ToggleRow(Bi("Show date", "顯示日期"), s.clockDate) { v ->
                vm.update { it.copy(clockDate = v) }
            }
            Field(Bi("Prefix", "前綴"), s.clockPrefix) { v -> vm.update { it.copy(clockPrefix = v) } }
        }

        Section(Bi("General", "一般"), Bi("App behaviour.", "應用程式行為。")) {
            ToggleRow(Bi("Auto-save", "自動儲存"), s.autoSave) { v ->
                vm.update { it.copy(autoSave = v) }
            }
        }

        Section(Bi("Bambu Printer LAN bundle", "Bambu Printer LAN 套件"),
            Bi("Export/import all settings as a .bambulan payload.",
                "將所有設定匯出／匯入做 .bambulan 內容。")) {
            var importText by remember { mutableStateOf("") }
            OutlinedButton(onClick = { vm.exportBundle() }) {
                Text(Bi("Export to clipboard", "匯出到剪貼簿").inline)
            }
            OutlinedTextField(
                value = importText, onValueChange = { importText = it },
                label = { Text(Bi("Paste .bambulan payload", "貼上 .bambulan 內容").inline) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { vm.importBundle(importText) }) {
                Text(Bi("Import bundle", "匯入套件").inline)
            }
        }

        Button(onClick = { vm.persist() }, modifier = Modifier.fillMaxWidth()) {
            Text(Bi("Save settings", "儲存設定").inline)
        }
    }
}

@Composable
private fun Section(title: Bi, subtitle: Bi, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BiText(title)
            BiBody(subtitle)
            content()
        }
    }
}

@Composable
private fun Field(label: Bi, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label.inline) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: Bi, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label.inline, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
