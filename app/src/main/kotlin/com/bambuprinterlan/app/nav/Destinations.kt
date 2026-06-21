package com.bambuprinterlan.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.ui.graphics.vector.ImageVector
import com.bambuprinterlan.core.design.Bi

/**
 * The simplified, less-confusing top-level flow from the plan (§3): three core
 * tabs plus Tools and Settings. Every label is bilingual (Cantonese + English).
 */
enum class Dest(val route: String, val label: Bi, val icon: ImageVector) {
    Prepare("prepare", Bi("Prepare", "準備"), Icons.Outlined.ViewInAr),
    Preview("preview", Bi("Preview", "預覽"), Icons.Outlined.Layers),
    Device("device", Bi("Device", "裝置"), Icons.Outlined.Print),
    Tools("tools", Bi("Tools", "工具"), Icons.Outlined.Build),
    Settings("settings", Bi("Settings", "設定"), Icons.Outlined.Settings);

    companion object {
        val bottomBar = listOf(Prepare, Preview, Device, Tools, Settings)
    }
}
