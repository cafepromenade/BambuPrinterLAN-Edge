package com.bambuprinterlan.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bambuprinterlan.net.bambu.LanMqttTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class QueueState { PENDING, UPLOADING, DONE, FAILED }

data class QueueItem(val uri: String, val name: String, val state: QueueState = QueueState.PENDING)

/**
 * Batch Printer Sender — port of BambuLan's batch printer sender
 * (MainFrame.cpp:6035). Uploads many sliced .3mf files to the printer SD over
 * FTPS (no live MQTT session needed). Start each job from the Device tab.
 */
class BatchViewModel(app: Application) : AndroidViewModel(app) {

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        val items = uris.map { uri ->
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "print.3mf"
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) name = c.getString(i)
                }
            }
            QueueItem(uri.toString(), name)
        }
        _queue.value = _queue.value + items
    }

    fun clear() { _queue.value = emptyList() }

    fun uploadAll(ip: String, accessCode: String, serial: String) {
        if (ip.isBlank() || accessCode.isBlank()) {
            _message.value = "Enter printer IP + access code  請填 IP 同存取碼"; return
        }
        if (_queue.value.isEmpty()) { _message.value = "Queue is empty  佇列係空"; return }
        viewModelScope.launch {
            _busy.value = true
            val transport = LanMqttTransport(ip, accessCode, serial.ifBlank { "batch" })
            val resolver = getApplication<Application>().contentResolver
            var ok = 0
            _queue.value.forEachIndexed { index, item ->
                setState(index, QueueState.UPLOADING)
                val result = runCatching {
                    val bytes = resolver.openInputStream(Uri.parse(item.uri))?.use { it.readBytes() }
                        ?: error("read failed")
                    var remote = item.name
                    if (!remote.endsWith(".3mf", true)) remote += ".3mf"
                    transport.upload(remote, bytes)
                }
                if (result.isSuccess) { setState(index, QueueState.DONE); ok++ }
                else setState(index, QueueState.FAILED)
            }
            _busy.value = false
            _message.value = "Uploaded $ok/${_queue.value.size}  已上載"
        }
    }

    private fun setState(index: Int, state: QueueState) {
        _queue.value = _queue.value.mapIndexed { i, it -> if (i == index) it.copy(state = state) else it }
    }

    fun clearMessage() { _message.value = null }
}
