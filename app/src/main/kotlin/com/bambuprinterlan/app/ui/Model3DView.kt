package com.bambuprinterlan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bambuprinterlan.app.Mesh
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Software 3D viewport — orbit by dragging, pinch to zoom. Renders the mesh with
 * depth-sorted, Lambert-shaded triangles. [userScale]/[userRotZ] mirror the Model
 * Edit transform so edits are reflected live.
 */
@Composable
fun Model3DView(
    mesh: Mesh, userScale: Float, userRotZ: Float,
    userRotX: Float = 0f, userRotY: Float = 0f, modifier: Modifier = Modifier,
) {
    var azim by remember { mutableFloatStateOf(0.6f) }
    var elev by remember { mutableFloatStateOf(-1.1f) }
    var zoom by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, z, _ -> zoom = (zoom * z).coerceIn(0.3f, 6f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    azim += drag.x * 0.01f
                    elev += drag.y * 0.01f
                }
            },
    ) {
        val n = mesh.count
        if (n == 0) return@Canvas
        val w = size.width; val h = size.height
        val fit = (min(w, h) / 2f) / mesh.radius * 0.85f * zoom
        val cxp = w / 2f; val cyp = h / 2f

        // Combined rotation: transform rot-Z, then orbit azim (Z) and elev (X).
        val tz = Math.toRadians(userRotZ.toDouble()).toFloat()
        val ca = cos(azim + tz); val sa = sin(azim + tz)
        val ce = cos(elev); val se = sin(elev)
        val rx = Math.toRadians(userRotX.toDouble()).toFloat()
        val ry = Math.toRadians(userRotY.toDouble()).toFloat()
        val crx = cos(rx); val srx = sin(rx); val cry = cos(ry); val sry = sin(ry)
        val light = floatArrayOf(0.3f, 0.4f, 0.85f)

        data class Face(val path: Path, val depth: Float, val shade: Float)
        val faces = ArrayList<Face>(n)

        // Decimate very large meshes so dragging stays smooth.
        val step = if (n > 7000) n / 7000 else 1
        val base = Color(0xFF4FA3F7)

        var t = 0
        while (t < n) {
            val o = t * 9
            val px = FloatArray(3); val py = FloatArray(3); val rz = FloatArray(3)
            var nx = 0f; var ny = 0f; var nz = 0f
            val rvx = FloatArray(3); val rvy = FloatArray(3); val rvz = FloatArray(3)
            for (k in 0 until 3) {
                val x0 = (mesh.tris[o + k * 3] - mesh.cx) * userScale
                val y0 = (mesh.tris[o + k * 3 + 1] - mesh.cy) * userScale
                val z0 = (mesh.tris[o + k * 3 + 2] - mesh.cz) * userScale
                // model tilt: rotate X then Y
                val ya = y0 * crx - z0 * srx; val za = y0 * srx + z0 * crx
                val x = x0 * cry + za * sry; val z = -x0 * sry + za * cry
                val y = ya
                // rotate about Z (azim)
                val x1 = x * ca - y * sa; val y1 = x * sa + y * ca
                // rotate about X (elev)
                val y2 = y1 * ce - z * se; val z2 = y1 * se + z * ce
                rvx[k] = x1; rvy[k] = y2; rvz[k] = z2
                px[k] = cxp + x1 * fit
                py[k] = cyp - z2 * fit   // up = +z after elevation
                rz[k] = y2
            }
            // face normal (cross product) for shading
            val ax = rvx[1] - rvx[0]; val ay = rvy[1] - rvy[0]; val az = rvz[1] - rvz[0]
            val bx = rvx[2] - rvx[0]; val by = rvy[2] - rvy[0]; val bz = rvz[2] - rvz[0]
            nx = ay * bz - az * by; ny = az * bx - ax * bz; nz = ax * by - ay * bx
            val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            val dot = (nx * light[0] + ny * light[1] + nz * light[2]) / nl
            val shade = (0.35f + 0.65f * max(0f, kotlin.math.abs(dot))).coerceIn(0.2f, 1f)

            val path = Path().apply {
                moveTo(px[0], py[0]); lineTo(px[1], py[1]); lineTo(px[2], py[2]); close()
            }
            faces.add(Face(path, (rz[0] + rz[1] + rz[2]) / 3f, shade))
            t += step
        }

        faces.sortBy { it.depth }   // far (small y) first
        for (f in faces) {
            drawPath(f.path, Color(base.red * f.shade, base.green * f.shade, base.blue * f.shade, 1f))
        }
    }
}
