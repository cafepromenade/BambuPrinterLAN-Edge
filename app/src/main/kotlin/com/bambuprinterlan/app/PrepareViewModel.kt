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

    private val supported = setOf("stl", "obj", "3mf", "step", "stp", "amf")

    init {
        // Restore the auto-saved workspace.
        viewModelScope.launch { _models.value = parseWorkspace(repo.workspaceJson().first()) }
        // Periodic auto-save (port of the 120s desktop auto-save timer).
        viewModelScope.launch {
            while (true) {
                delay(120_000)
                if (repo.getBool("material_auto_save").first()) persistWorkspace()
            }
        }
    }

    private suspend fun persistWorkspace() = repo.saveWorkspace(serializeWorkspace(_models.value))

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
        autoSaveNow()
    }

    fun importMany(uris: List<Uri>) {
        uris.forEach { import(it) }
        if (uris.size > 1) _message.value = "Imported ${uris.size} files  已匯入 ${uris.size} 個檔案"
    }

    fun remove(model: ImportedModel) {
        _models.value = _models.value - model
        autoSaveNow()
    }

    fun slice() {
        val model = _models.value.firstOrNull()
        if (model == null) { _message.value = "Import a model first  請先匯入模型"; return }
        val ext = model.name.substringAfterLast('.', "").lowercase()
        if (ext != "stl" && ext != "obj" && ext != "3mf") {
            _message.value = "Engine slices STL/OBJ/3MF; $ext support is coming  暫支援 STL／OBJ／3MF"
            return
        }
        viewModelScope.launch {
            _message.value = "Slicing ${model.name}…  切片緊…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val input = File(ctx.cacheDir, "input.$ext")
                    ctx.contentResolver.openInputStream(Uri.parse(model.uri))?.use { src ->
                        input.outputStream().use { src.copyTo(it) }
                    } ?: error("Could not read model")
                    // 3MF: unzip + parse mesh -> binary STL the engine slices.
                    val meshFile = if (ext == "3mf") {
                        val stl = File(ctx.cacheDir, "input3mf.stl")
                        if (!Mesh3mf.toStl(input.inputStream(), stl)) error("Could not parse 3MF")
                        stl
                    } else input
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
                    SliceStore.set(SliceResult(model.name, out.absolutePath, layers, out.length(), head))
                    _sliced.tryEmit(Unit)
                    _message.value = "Sliced: $layers layers → ${out.name} (${out.length() / 1024} KB)  已切片"
                } else _message.value = "Slice failed (code $layers)  切片失敗"
            }.onFailure { _message.value = "Slice error  切片錯誤: ${it.message ?: ""}" }
        }
    }

    /** Emits when a slice finishes — the screen navigates to Preview. */
    val sliced = _sliced.asSharedFlow()

    fun clearMessage() { _message.value = null }
}
