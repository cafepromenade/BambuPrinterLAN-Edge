package com.bambuprinterlan.app

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A triangle soup ready for the software 3D viewport. */
class Mesh(val tris: FloatArray) {           // 9 floats per triangle
    val count = tris.size / 9
    var cx = 0f; var cy = 0f; var cz = 0f; var radius = 1f
    var minX = 0f; var maxX = 0f; var minY = 0f; var maxY = 0f; var minZ = 0f; var maxZ = 0f
    val width get() = maxX - minX
    val depth get() = maxY - minY
    init {
        if (count > 0) {
            var minx = Float.MAX_VALUE; var miny = Float.MAX_VALUE; var minz = Float.MAX_VALUE
            var maxx = -Float.MAX_VALUE; var maxy = -Float.MAX_VALUE; var maxz = -Float.MAX_VALUE
            var i = 0
            while (i < tris.size) {
                val x = tris[i]; val y = tris[i + 1]; val z = tris[i + 2]
                if (x < minx) minx = x; if (x > maxx) maxx = x
                if (y < miny) miny = y; if (y > maxy) maxy = y
                if (z < minz) minz = z; if (z > maxz) maxz = z
                i += 3
            }
            minX = minx; maxX = maxx; minY = miny; maxY = maxy; minZ = minz; maxZ = maxz
            cx = (minx + maxx) / 2; cy = (miny + maxy) / 2; cz = (minz + maxz) / 2
            val dx = maxx - minx; val dy = maxy - miny; val dz = maxz - minz
            radius = (maxOf(dx, dy, dz) / 2).coerceAtLeast(0.001f)
        }
    }
}

/** Parses STL (binary/ASCII), OBJ, or 3MF into a [Mesh]. */
object MeshLoader {
    fun load(input: InputStream, ext: String): Mesh? = runCatching {
        val bytes = input.readBytes()
        val tris = when (ext.lowercase()) {
            "3mf" -> {
                val tmp = File.createTempFile("m3mf", ".stl")
                if (!Mesh3mf.toStl(ByteArrayInputStream(bytes), tmp)) return null
                val stl = tmp.readBytes(); tmp.delete(); parseStl(stl)
            }
            "obj" -> parseObj(bytes)
            else -> parseStl(bytes)
        }
        if (tris.isEmpty()) null else Mesh(tris)
    }.getOrNull()

    private fun isAsciiStl(b: ByteArray): Boolean {
        if (b.size < 6) return false
        val head = String(b, 0, 5)
        if (head != "solid") return false
        if (b.size >= 84) {
            val c = ByteBuffer.wrap(b, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (b.size.toLong() == 84L + 50L * c) return false
        }
        return true
    }

    private fun parseStl(b: ByteArray): FloatArray =
        if (isAsciiStl(b)) parseAsciiStl(b) else parseBinaryStl(b)

    private fun parseBinaryStl(b: ByteArray): FloatArray {
        if (b.size < 84) return FloatArray(0)
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var c = buf.getInt(80)
        if (b.size.toLong() < 84L + 50L * c) c = ((b.size - 84) / 50)
        val out = FloatArray(c * 9)
        var p = 84; var o = 0
        for (i in 0 until c) {
            p += 12 // skip normal
            for (k in 0 until 9) { out[o++] = buf.getFloat(p); p += 4 }
            p += 2  // attribute
        }
        return out
    }

    private fun parseAsciiStl(b: ByteArray): FloatArray {
        val out = ArrayList<Float>()
        String(b).lineSequence().forEach { line ->
            val t = line.trim()
            if (t.startsWith("vertex")) {
                val p = t.split(Regex("\\s+"))
                if (p.size >= 4) {
                    out.add(p[1].toFloatOrNull() ?: 0f)
                    out.add(p[2].toFloatOrNull() ?: 0f)
                    out.add(p[3].toFloatOrNull() ?: 0f)
                }
            }
        }
        return out.toFloatArray()
    }

    private fun parseObj(b: ByteArray): FloatArray {
        val vx = ArrayList<Float>()
        val out = ArrayList<Float>()
        String(b).lineSequence().forEach { line ->
            when {
                line.startsWith("v ") -> {
                    val p = line.substring(2).trim().split(Regex("\\s+"))
                    if (p.size >= 3) { vx.add(p[0].toFloatOrNull() ?: 0f); vx.add(p[1].toFloatOrNull() ?: 0f); vx.add(p[2].toFloatOrNull() ?: 0f) }
                }
                line.startsWith("f ") -> {
                    val idx = line.substring(2).trim().split(Regex("\\s+")).mapNotNull {
                        it.substringBefore('/').toIntOrNull()?.let { n -> if (n < 0) vx.size / 3 + n else n - 1 }
                    }
                    for (i in 2 until idx.size) {
                        for (vi in intArrayOf(idx[0], idx[i - 1], idx[i])) {
                            val o = vi * 3
                            if (o in 0..vx.size - 3) { out.add(vx[o]); out.add(vx[o + 1]); out.add(vx[o + 2]) }
                        }
                    }
                }
            }
        }
        return out.toFloatArray()
    }
}
