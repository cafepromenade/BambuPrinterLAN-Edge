package com.bambuprinterlan.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Imports a 3MF (zip of 3D/3dmodel.model XML) by parsing its mesh and writing a
 * binary STL the native engine can slice. Keeps the engine STL/OBJ-only while
 * supporting Bambu's primary format.
 */
object Mesh3mf {

    /** Parse [input] (a .3mf stream) and write a binary STL to [out]. Returns true on success. */
    fun toStl(input: InputStream, out: File): Boolean = runCatching {
        val modelXml = extractModelXml(input) ?: return false
        val (verts, tris) = parseModel(modelXml)
        if (tris.isEmpty()) return false
        writeBinaryStl(verts, tris, out)
        true
    }.getOrDefault(false)

    private fun extractModelXml(input: InputStream): ByteArray? {
        ZipInputStream(input).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (e.name.endsWith("3dmodel.model", ignoreCase = true)) return zis.readBytes()
                e = zis.nextEntry
            }
        }
        return null
    }

    private fun parseModel(xml: ByteArray): Pair<FloatArray, IntArray> {
        val verts = ArrayList<Float>()
        val tris = ArrayList<Int>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), null)
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vertex" -> {
                        verts.add(attr(parser, "x")); verts.add(attr(parser, "y")); verts.add(attr(parser, "z"))
                    }
                    "triangle" -> {
                        tris.add(attrI(parser, "v1")); tris.add(attrI(parser, "v2")); tris.add(attrI(parser, "v3"))
                    }
                }
            }
            ev = parser.next()
        }
        return verts.toFloatArray() to tris.toIntArray()
    }

    private fun attr(p: XmlPullParser, n: String) = p.getAttributeValue(null, n)?.toFloatOrNull() ?: 0f
    private fun attrI(p: XmlPullParser, n: String) = p.getAttributeValue(null, n)?.toIntOrNull() ?: 0

    private fun writeBinaryStl(verts: FloatArray, tris: IntArray, out: File) {
        val count = tris.size / 3
        val buf = ByteBuffer.allocate(84 + count * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80)
        buf.putInt(count)
        for (i in 0 until count) {
            buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f)  // normal
            for (k in 0..2) {
                val v = tris[i * 3 + k] * 3
                buf.putFloat(verts.getOrElse(v) { 0f })
                buf.putFloat(verts.getOrElse(v + 1) { 0f })
                buf.putFloat(verts.getOrElse(v + 2) { 0f })
            }
            buf.putShort(0)  // attribute byte count
        }
        out.outputStream().use { it.write(buf.array()) }
    }
}
