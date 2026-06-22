package com.bambuprinterlan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.core.design.Bi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Parsed toolpaths: one entry per layer, each a flat [x0,y0,x1,y1,...] of extrusion moves. */
class GcodePaths(val layers: List<FloatArray>, val minX: Float, val minY: Float, val w: Float, val h: Float)

private fun parseGcode(path: String): GcodePaths? = runCatching {
    val layers = ArrayList<ArrayList<Float>>()
    var cur = ArrayList<Float>()
    var x = 0f; var y = 0f; var rel = true
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    File(path).useLines { lines ->
        lines.forEach { raw ->
            if (raw.startsWith("; layer")) { if (cur.isNotEmpty()) { layers.add(cur); cur = ArrayList() } }
            val ln = raw.substringBefore(';').trim()
            if (ln.startsWith("M82")) rel = false else if (ln.startsWith("M83")) rel = true
            else if (ln.startsWith("G0") || ln.startsWith("G1")) {
                var nx = x; var ny = y; var e = 0f
                ln.split(' ').forEach { t ->
                    when (t.firstOrNull()) {
                        'X' -> t.drop(1).toFloatOrNull()?.let { nx = it }
                        'Y' -> t.drop(1).toFloatOrNull()?.let { ny = it }
                        'E' -> t.drop(1).toFloatOrNull()?.let { e = it }
                    }
                }
                val extruding = if (rel) e > 0.0001f else e > 0f
                if (extruding && ln.startsWith("G1")) {
                    cur.add(x); cur.add(y); cur.add(nx); cur.add(ny)
                    minX = minOf(minX, x, nx); minY = minOf(minY, y, ny)
                    maxX = maxOf(maxX, x, nx); maxY = maxOf(maxY, y, ny)
                }
                x = nx; y = ny
            }
        }
    }
    if (cur.isNotEmpty()) layers.add(cur)
    if (layers.isEmpty() || minX > maxX) return null
    GcodePaths(layers.map { it.toFloatArray() }, minX, minY, (maxX - minX).coerceAtLeast(1f), (maxY - minY).coerceAtLeast(1f))
}.getOrNull()

/** Layer-by-layer toolpath preview with a slider. */
@Composable
fun GcodeLayerView(gcodePath: String, modifier: Modifier = Modifier) {
    val paths by produceState<GcodePaths?>(initialValue = null, gcodePath) {
        value = withContext(Dispatchers.IO) { parseGcode(gcodePath) }
    }
    val p = paths ?: return
    var layer by remember { mutableFloatStateOf((p.layers.size - 1).toFloat()) }
    val idx = layer.toInt().coerceIn(0, p.layers.lastIndex)
    val line = MaterialTheme.colorScheme.primary

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Bi("Layer ${idx + 1} / ${p.layers.size}", "層 ${idx + 1} / ${p.layers.size}").inline,
            style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            val scale = minOf(size.width / p.w, size.height / p.h) * 0.9f
            val ox = (size.width - p.w * scale) / 2f
            val oy = (size.height - p.h * scale) / 2f
            fun px(x: Float) = ox + (x - p.minX) * scale
            fun py(y: Float) = oy + (p.h - (y - p.minY)) * scale  // flip Y
            val seg = p.layers[idx]
            var i = 0
            while (i + 3 < seg.size) {
                drawLine(line, Offset(px(seg[i]), py(seg[i + 1])), Offset(px(seg[i + 2]), py(seg[i + 3])),
                    strokeWidth = 2f)
                i += 4
            }
        }
        Slider(layer, { layer = it }, valueRange = 0f..(p.layers.size - 1).coerceAtLeast(1).toFloat())
    }
}
