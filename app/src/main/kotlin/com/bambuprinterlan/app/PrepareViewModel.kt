package com.bambuprinterlan.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bambuprinterlan.core.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A model file the user imported into the workspace. */
data class ImportedModel(val name: String, val uri: String, val sizeBytes: Long)

/**
 * Prepare workspace state: the imported model list, with real Auto-Save —
 * the workspace is restored on launch and persisted (DataStore) on every change
 * and on a 120s timer when material_auto_save is on. Slicing is handed to the
 * native engine (`:engine:jni`).
 */
class PrepareViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    private val _models = MutableStateFlow<List<ImportedModel>>(emptyList())
    val models: StateFlow<List<ImportedModel>> = _models.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _sliced = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val supported = setOf("stl", "obj", "3mf", "step", "stp", "amf", "gcode")

    init {
        // Restore the auto-saved workspace.
        viewModelScope.launch {
            _models.value = parseWorkspace(repo.workspaceJson().first())
            publishFirst()
        }
        // Periodic auto-save (port of the 120s desktop auto-save timer).
        viewModelScope.launch {
            while (true) {
                delay(120_000)
                if (repo.getBool("material_auto_save").first()) persistWorkspace()
            }
        }
    }

    private suspend fun persistWorkspace() = repo.saveWorkspace(serializeWorkspace(_models.value))

    private fun publishFirst() {
        val m = _models.value.firstOrNull()
        WorkspaceStore.setFirst(m?.uri, m?.name)
    }

    private fun autoSaveNow() {
        viewModelScope.launch { if (repo.getBool("material_auto_save").first()) persistWorkspace() }
    }

    private fun parseWorkspace(json: String): List<ImportedModel> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ImportedModel(o.optString("name"), o.optString("uri"), o.optLong("size"))
        }
    }.getOrDefault(emptyList())

    private fun serializeWorkspace(models: List<ImportedModel>): String {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(JSONObject().put("name", m.name).put("uri", m.uri).put("size", m.sizeBytes))
        }
        return arr.toString()
    }

    fun import(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var name = uri.lastPathSegment ?: "model"
        var size = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in supported) {
            _message.value = "Unsupported: .$ext  唔支援"
            return
        }
        _models.value = _models.value + ImportedModel(name, uri.toString(), size)
        _message.value = "Imported $name  已匯入"
        publishFirst()
        autoSaveNow()
    }

    fun importMany(uris: List<Uri>) {
        uris.forEach { import(it) }
        if (uris.size > 1) _message.value = "Imported ${uris.size} files  已匯入 ${uris.size} 個檔案"
    }

    fun remove(model: ImportedModel) {
        _models.value = _models.value - model
        publishFirst()
        autoSaveNow()
    }

    fun slice() {
        val model = _models.value.firstOrNull()
        if (model == null) { _message.value = "Import a model first  請先匯入模型"; return }
        val ext = model.name.substringAfterLast('.', "").lowercase()
        if (ext == "gcode") { loadGcode(model); return }
        if (ext != "stl" && ext != "obj" && ext != "3mf") {
            _message.value = "Engine slices STL/OBJ/3MF; $ext support is coming  暫支援 STL／OBJ／3MF"
            return
        }
        // Multi-object: auto-arrange every mesh model onto one plate.
        val meshModels = _models.value.filter {
            it.name.substringAfterLast('.', "").lowercase() in setOf("stl", "obj", "3mf")
        }
        val multi = meshModels.size > 1
        val label = if (multi) "${meshModels.size} models" else model.name
        viewModelScope.launch {
            _message.value = "Slicing $label…  切片緊…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val meshFile: File
                    if (multi) {
                        val combined = File(ctx.cacheDir, "combined.stl")
                        val items = meshModels.map { it.uri to it.name.substringAfterLast('.', "").lowercase() }
                        if (!ModelArranger.combine(ctx, items, combined)) error("Could not arrange models")
                        meshFile = combined
                    } else {
                        val input = File(ctx.cacheDir, "input.$ext")
                        ctx.contentResolver.openInputStream(Uri.parse(model.uri))?.use { src ->
                            input.outputStream().use { src.copyTo(it) }
                        } ?: error("Could not read model")
                        // 3MF: unzip + parse mesh -> binary STL the engine slices.
                        meshFile = if (ext == "3mf") {
                            val stl = File(ctx.cacheDir, "input3mf.stl")
                            if (!Mesh3mf.toStl(input.inputStream(), stl)) error("Could not parse 3MF")
                            stl
                        } else input
                    }
                    val out = File(ctx.cacheDir, "output.gcode")
                    val layers = com.bambuprinterlan.engine.SlicerBridge.slice(
                        meshFile.absolutePath, out.absolutePath, ModelEditStore.configIni(),
                    )
                    Pair(layers, out)
                }
            }
            result.onSuccess { (layers, out) ->
                if (layers >= 0) {
                    val head = runCatching { out.useLines { it.take(40).joinToString("\n") } }.getOrDefault("")
                    val (m, g, mins) = estimate(out)
                    SliceStore.set(SliceResult(label, out.absolutePath, layers, out.length(), head, m, g, mins))
                    _sliced.tryEmit(Unit)
                    _message.value = "Sliced: $layers layers → ${out.name} (${out.length() / 1024} KB)  已切片"
                } else _message.value =
                    "Couldn't prepare this model. Try another STL/OBJ/3MF file.  無法處理此模型，請換另一個檔案。"
            }.onFailure {
                _message.value = "Couldn't prepare this model. Try another file.  無法處理此模型，請換另一個檔案。"
            }
        }
    }

    /** Estimate filament (m, g) and time (min) by walking the G-code. */
    private fun estimate(out: File): Triple<Float, Float, Int> = runCatching {
        var x = 0f; var y = 0f; var z = 0f; var feed = 1500f
        var eSum = 0f; var seconds = 0f
        val rel = BooleanArray(1) { true }  // engine uses M83 (relative E)
        out.useLines { lines ->
            lines.forEach { raw ->
                val ln = raw.substringBefore(';').trim()
                if (ln.startsWith("M82")) rel[0] = false
                else if (ln.startsWith("M83")) rel[0] = true
                else if (ln.startsWith("G0") || ln.startsWith("G1")) {
                    var nx = x; var ny = y; var nz = z; var e = 0f
                    ln.split(' ').forEach { tok ->
                        when (tok.firstOrNull()) {
                            'X' -> tok.drop(1).toFloatOrNull()?.let { nx = it }
                            'Y' -> tok.drop(1).toFloatOrNull()?.let { ny = it }
                            'Z' -> tok.drop(1).toFloatOrNull()?.let { nz = it }
                            'E' -> tok.drop(1).toFloatOrNull()?.let { e = it }
                            'F' -> tok.drop(1).toFloatOrNull()?.let { feed = it }
                        }
                    }
                    val dist = kotlin.math.sqrt((nx - x) * (nx - x) + (ny - y) * (ny - y) + (nz - z) * (nz - z))
                    if (feed > 0f) seconds += dist / (feed / 60f)
                    if (e > 0f) eSum += if (rel[0]) e else 0f  // count positive relative extrusion
                    x = nx; y = ny; z = nz
                }
            }
        }
        val meters = eSum / 1000f
        val grams = (eSum * 2.405f) / 1000f * 1.24f      // volume(mm3)->cm3 * PLA density
        Triple(meters, grams, (seconds / 60f).toInt())
    }.getOrDefault(Triple(0f, 0f, 0))

    /** Load an existing .gcode directly (no slicing) and jump to Preview. */
    private fun loadGcode(model: ImportedModel) {
        viewModelScope.launch {
            _message.value = "Loading ${model.name}…  載入緊…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val out = File(ctx.cacheDir, "output.gcode")
                    ctx.contentResolver.openInputStream(Uri.parse(model.uri))?.use { src ->
                        out.outputStream().use { src.copyTo(it) }
                    } ?: error("Could not read file")
                    // Count layers: explicit markers, else distinct Z moves.
                    var markers = 0; var zMoves = 0
                    out.useLines { lines ->
                        lines.forEach { ln ->
                            val t = ln.trimStart()
                            if (t.startsWith(";LAYER") || t.startsWith("; layer")) markers++
                            else if (t.startsWith("G1 Z") || t.startsWith("G0 Z")) zMoves++
                        }
                    }
                    val layers = if (markers > 0) markers else zMoves
                    val head = runCatching { out.useLines { it.take(40).joinToString("\n") } }.getOrDefault("")
                    Triple(layers, out, head)
                }
            }
            result.onSuccess { (layers, out, head) ->
                val (m, g, mins) = estimate(out)
                SliceStore.set(SliceResult(model.name, out.absolutePath, layers, out.length(), head, m, g, mins))
                _sliced.tryEmit(Unit)
                _message.value = "Loaded G-code: $layers layers  已載入 G-code"
            }.onFailure { _message.value = "Couldn't read this G-code.  無法讀取此 G-code。" }
        }
    }

    /** Emits when a slice finishes — the screen navigates to Preview. */
    val sliced = _sliced.asSharedFlow()

    fun clearMessage() { _message.value = null }
}
