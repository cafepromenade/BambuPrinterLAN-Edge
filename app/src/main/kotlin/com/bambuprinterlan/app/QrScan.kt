package com.bambuprinterlan.app

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

/** A printer's connection details parsed from a scanned QR code. */
data class ScannedPrinter(val serial: String, val ip: String, val accessCode: String)

/**
 * Launch the Google Code Scanner (no camera permission needed — it runs in
 * Play Services) and return the raw QR text. Quick-add for printers.
 */
fun scanQrCode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode ->
            val v = barcode.rawValue
            if (v.isNullOrBlank()) onError("Empty QR  空白 QR") else onResult(v)
        }
        .addOnCanceledListener { /* user cancelled */ }
        .addOnFailureListener { onError(it.message ?: "Scan failed  掃描失敗") }
}

/**
 * Parse a printer QR. Accepts JSON ({"sn"/"serial","ip","access_code"}),
 * key=value (query string or lines), or a plain serial string.
 */
fun parsePrinterQr(text: String): ScannedPrinter {
    val t = text.trim()
    // JSON
    runCatching {
        val o = JSONObject(t)
        val serial = firstNonBlank(o.optString("sn"), o.optString("serial"), o.optString("dev_id"))
        val ip = firstNonBlank(o.optString("ip"), o.optString("dev_ip"))
        val code = firstNonBlank(o.optString("access_code"), o.optString("accessCode"), o.optString("code"))
        if (serial.isNotBlank() || ip.isNotBlank() || code.isNotBlank())
            return ScannedPrinter(serial, ip, code)
    }
    // key=value (handles "k=v&k=v", "k=v;k=v", or newline-separated, incl. URLs)
    if (t.contains('=')) {
        val body = t.substringAfter('?', t)
        val map = body.split('&', ';', '\n', ',')
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i > 0) part.take(i).trim().lowercase() to part.substring(i + 1).trim() else null
            }.toMap()
        val serial = firstNonBlank(map["sn"], map["serial"], map["dev_id"], map["devid"])
        val ip = firstNonBlank(map["ip"], map["dev_ip"], map["host"])
        val code = firstNonBlank(map["access_code"], map["accesscode"], map["access"], map["code"])
        if (serial.isNotBlank() || ip.isNotBlank() || code.isNotBlank())
            return ScannedPrinter(serial, ip, code)
    }
    // Plain serial
    return ScannedPrinter(t, "", "")
}

private fun firstNonBlank(vararg s: String?): String = s.firstOrNull { !it.isNullOrBlank() }?.trim() ?: ""
