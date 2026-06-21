package com.bambuprinterlan.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * BambuPrinterLan design system — a faithful port of BambuLan's `MaterialDesign::*`
 * role tokens to Material 3. The custom layer used M3 roles (Primary,
 * PrimaryContainer, Surface, Outline, …); these are the M3 baseline values the
 * tokens approximate, so brand identity carries over. Dynamic color can layer on
 * top at the app level for Android 12+.
 */
private val Purple = Color(0xFF6750A4)        // MaterialDesign::Primary (autofix embed 0x6750A4)
private val PurpleHover = Color(0xFF7965B5)
private val OnPurple = Color(0xFFFFFFFF)
private val PurpleContainer = Color(0xFFEADDFF)
private val OnPurpleContainer = Color(0xFF21005D)
private val BambuBlue = Color(0xFF1A73E8)     // material_clock_color default / default embed

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = OnPurple,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = OnPurpleContainer,
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF006A6A),             // project embed 0x006A6A
    error = Color(0xFFB3261E),                // error embed 0xB3261E
    surface = Color(0xFFFEF7FF),
    surfaceVariant = Color(0xFFE7E0EC),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = PurpleContainer,
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFF4DD8D8),
    error = Color(0xFFF2B8B5),
    surface = Color(0xFF141218),
    surfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

/** Brand accent available outside the M3 scheme (e.g. the Material Clock). */
val BambuAccentBlue: Color get() = BambuBlue

@Composable
fun BambuPrinterLanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
