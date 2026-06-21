package com.bambuprinterlan.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Bilingual string — Cantonese (粵語) and English shown TOGETHER, never a toggle.
 * The whole app is bilingual by requirement, so every user-facing label is a [Bi].
 *
 * @property en English text
 * @property yue Cantonese / 繁體中文 (Hong Kong) text
 */
data class Bi(val en: String, val yue: String) {
    /** Single-line form: "English 粵語" — good for chips, buttons, nav labels. */
    val inline: String get() = "$en  $yue"

    /** Reversed single-line: "粵語 English". */
    val inlineYueFirst: String get() = "$yue  $en"
}

/** Stacked bilingual label: English on top, Cantonese beneath in a muted tone. */
@Composable
fun BiText(
    bi: Bi,
    modifier: Modifier = Modifier,
    enSize: TextUnit = MaterialTheme.typography.titleSmall.fontSize,
    maxLines: Int = 2,
) {
    Column(modifier) {
        Text(
            text = bi.en,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = enSize),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = bi.yue,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bilingual body text: English line over Cantonese line, body styling. */
@Composable
fun BiBody(bi: Bi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text = bi.en, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = bi.yue,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
    }
}
