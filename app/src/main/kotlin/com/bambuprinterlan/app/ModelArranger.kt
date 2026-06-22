package com.bambuprinterlan.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Loads several models, auto-arranges them in a grid on the plate, and writes a
 * single combined binary STL the native engine slices as one plate.
 */
object ModelArranger {
    private const val GAP = 8f  // mm between parts

    fun combine(context: Context, items: List<Pair<String, String>>, out: File): Boolean = runCatching {
        val meshes = items.mapNotNull { (uri, ext) ->
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { MeshLoader.load(it, ext) }
        }.filter { it.count > 0 }
        if (meshes.isEmpty()) return false

        val cols = ceil(sqrt(meshes.size.toDouble())).toInt().coerceAtLeast(1)
        val cell = meshes.maxOf { maxOf(it.width, it.depth) } + GAP

        val all = ArrayList<Float>(meshes.sumOf { it.tris.size })
        meshes.forEachIndexed { i, m ->
            val col = i % cols; val row = i / cols
            val targetX = col * cell; val targetY = row * cell
            val dx = targetX - m.cx; val dy = targetY - m.cy
            var k = 0
            while (k < m.tris.size) {
                all.add(m.tris[k] + dx); all.add(m.tris[k + 1] + dy); all.add(m.tris[k + 2])
                k += 3
            }
        }
        writeBinaryStl(all.toFloatArray(), out)
        true
    }.getOrDefault(false)

    private fun writeBinaryStl(verts: FloatArray, out: File) {
        val count = verts.size / 9
        val buf = ByteBuffer.allocate(84 + count * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80); buf.putInt(count)
        var i = 0
        repeat(count) {
            buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)  // normal
            repeat(9) { buf.putFloat(verts[i++]) }
            buf.putShort(0)
        }
        out.outputStream().use { it.write(buf.array()) }
    }
}
