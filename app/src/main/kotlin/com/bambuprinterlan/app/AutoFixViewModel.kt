package com.bambuprinterlan.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bambuprinterlan.core.data.SettingsRepository
import com.bambuprinterlan.net.bambu.integrations.AiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class TaskStatus { PENDING, RUNNING, RETRYING, DONE, FAILED }

data class FixTask(
    val id: Long,
    val prompt: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val attempts: Int = 0,
    val output: String = "",
)

/**
 * Report-a-Bug + Auto-Fix Dashboard — port of BambuLan's opencode self-fix
 * dashboard. Each task runs the model client with up to material_self_fix_retry_limit
 * retries; an optional sandbox (material_auto_fix_sandbox) requests a no-write
 * dry-run plan first. Progress markers mirror the desktop ([IN PROGRESS]/[RETRYING]/
 * [DONE]/[FAILED]).
 */
class AutoFixViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val ai = AiClient()
    private var nextId = 1L

    private val _tasks = MutableStateFlow<List<FixTask>>(emptyList())
    val tasks: StateFlow<List<FixTask>> = _tasks.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun add(prompt: String) {
        if (prompt.isBlank()) return
        _tasks.value = _tasks.value + FixTask(nextId++, prompt.trim())
    }

    fun remove(id: Long) { _tasks.value = _tasks.value.filterNot { it.id == id } }

    fun clear() { _tasks.value = emptyList() }

    fun runAll() {
        if (_busy.value) return
        viewModelScope.launch {
            val key = repo.getString("anthropic_api_key").first()
            if (key.isBlank()) {
                _tasks.value = _tasks.value.map { it.copy(status = TaskStatus.FAILED,
                    output = "Set your Anthropic API key in Settings  喺設定填 API key") }
                return@launch
            }
            val model = repo.getString("material_auto_fix_opencode_model").first()
            val limit = repo.getString("material_self_fix_retry_limit").first().toIntOrNull()?.coerceIn(1, 10) ?: 5
            val sandbox = repo.getBool("material_auto_fix_sandbox").first()
            _busy.value = true
            for (task in _tasks.value) {
                if (task.status == TaskStatus.DONE) continue
                runTask(task.id, key, model, limit, sandbox)
            }
            _busy.value = false
        }
    }

    private suspend fun runTask(id: Long, key: String, model: String, limit: Int, sandbox: Boolean) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        val prompt = (if (sandbox) "Provide a no-write dry-run PLAN only (do not modify anything):\n" else "") +
            "You are an auto-fix agent for a 3D-printer slicer app. Address this task:\n${task.prompt}"
        var attempt = 0
        while (attempt < limit) {
            attempt++
            update(id) { it.copy(status = if (attempt == 1) TaskStatus.RUNNING else TaskStatus.RETRYING, attempts = attempt) }
            val result = ai.complete(key, model, prompt, maxTokens = 1200)
            if (result.isSuccess) {
                update(id) { it.copy(status = TaskStatus.DONE, output = result.getOrDefault("")) }
                return
            }
            update(id) { it.copy(output = "Attempt $attempt failed: ${result.exceptionOrNull()?.message ?: ""}") }
        }
        update(id) { it.copy(status = TaskStatus.FAILED) }
    }

    private fun update(id: Long, transform: (FixTask) -> FixTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }
}
