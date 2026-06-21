package com.bambuprinterlan.core.data

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * `.bambulan` settings bundle — port of BambuLan's export/import
 * (MainFrame.cpp:6981 / :7039): a base64-encoded ZIP carrying the settings +
 * feature flags so configuration round-trips between desktop and Android.
 *
 * Format: ZIP { "bambulan.json": {format, version, strings{}, booleans{},
 * ints{}, features{}} }, then base64 (the desktop payload is also base64 text).
 */
object BambuLanBundle {
    private const val FORMAT = "bambulan-settings"
    private const val VERSION = 1
    private const val ENTRY = "bambulan.json"

    fun export(
        strings: Map<String, String>,
        booleans: Map<String, Boolean>,
        ints: Map<String, Int>,
        features: Map<String, Boolean>,
    ): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("strings", JSONObject(strings.toMap()))
            .put("booleans", JSONObject(booleans.mapValues { it.value }))
            .put("ints", JSONObject(ints.mapValues { it.value }))
            .put("features", JSONObject(features.mapValues { it.value }))

        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY))
            zos.write(root.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return Base64.encodeToString(zipped.toByteArray(), Base64.NO_WRAP)
    }

    data class Imported(
        val strings: Map<String, String>,
        val booleans: Map<String, Boolean>,
        val ints: Map<String, Int>,
        val features: Map<String, Boolean>,
    )

    /** Throws IllegalArgumentException on a malformed payload. */
    fun import(payloadBase64: String): Imported {
        val compact = payloadBase64.filterNot { it.isWhitespace() }
        val zipBytes = try {
            Base64.decode(compact, Base64.DEFAULT)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid .bambulan base64 payload.", e)
        }
        var json: String? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == ENTRY) { json = zis.readBytes().toString(Charsets.UTF_8); break }
                entry = zis.nextEntry
            }
        }
        val root = JSONObject(json ?: throw IllegalArgumentException("Missing $ENTRY in bundle."))
        return Imported(
            strings = root.optJSONObject("strings").toStringMap(),
            booleans = root.optJSONObject("booleans").toBoolMap(),
            ints = root.optJSONObject("ints").toIntMap(),
            features = root.optJSONObject("features").toBoolMap(),
        )
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { getString(it) }
    }

    private fun JSONObject?.toBoolMap(): Map<String, Boolean> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { optBoolean(it) }
    }

    private fun JSONObject?.toIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { optInt(it) }
    }
}
