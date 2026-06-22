package com.bambuprinterlan.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bambuprinterlan.core.data.SettingsRepository
import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.DeviceStatus
import com.bambuprinterlan.core.model.GcodeState
import com.bambuprinterlan.net.bambu.DeviceTransport
import com.bambuprinterlan.net.bambu.LanMqttTransport
import com.bambuprinterlan.net.bambu.RelayTransport
import com.bambuprinterlan.net.bambu.integrations.DiscordClient
import com.bambuprinterlan.net.bambu.integrations.HomeAssistantClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Live device control via the relay transport (LAN-direct MQTT lands in Phase 4).
 * Streams [DeviceStatus] and forwards [Command]s — the same command model the
 * desktop publishes, so behaviour matches BambuStudio's device control.
 */
class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    private val _status = MutableStateFlow<DeviceStatus?>(null)
    val status: StateFlow<DeviceStatus?> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _savedPrinters = MutableStateFlow<List<com.bambuprinterlan.core.model.Printer>>(emptyList())
    val savedPrinters: StateFlow<List<com.bambuprinterlan.core.model.Printer>> = _savedPrinters.asStateFlow()

    private val relayApi = com.bambuprinterlan.net.bambu.integrations.RelayApi()
    private val _relayPrinters = MutableStateFlow<List<com.bambuprinterlan.net.bambu.integrations.RelayApi.RelayPrinter>>(emptyList())
    val relayPrinters: StateFlow<List<com.bambuprinterlan.net.bambu.integrations.RelayApi.RelayPrinter>> = _relayPrinters.asStateFlow()

    private var lastState: GcodeState? = null

    private var transport: DeviceTransport? = null
    private var streamJob: Job? = null

    /** Connect through the configured relay. Token is optional (LAN-open relay). */
    fun connectViaRelay(serial: String) {
        if (serial.isBlank()) { _message.value = "Enter a serial  請輸入序號"; return }
        viewModelScope.launch {
            val baseUrl = repo.getString("relay_base_url").first()
            val token = repo.getString("relay_token").first()  // may be empty
            if (baseUrl.isBlank()) {
                _message.value = "Set relay URL in Settings  喺設定填中繼網址"
                return@launch
            }
            startStream(RelayTransport(baseUrl, token, serial), "Relay")
        }
    }

    init {
        viewModelScope.launch { repo.savedPrinters().collect { _savedPrinters.value = it } }
        // Notification action buttons (pause/resume/stop) route to the transport.
        CommandBus.handler = { cmd -> send(cmd) }
    }

    /** Drive the ongoing print notification from live status. */
    private fun updateNotification(st: DeviceStatus) {
        val app = getApplication<android.app.Application>()
        when (st.gcodeState) {
            GcodeState.RUNNING, GcodeState.PAUSE, GcodeState.PREPARE -> {
                val text = "${st.progressPercent}% · " +
                    "${app.getString(com.bambuprinterlan.app.R.string.app_name)} · " +
                    "layer ${st.layerNum}/${st.totalLayerNum} · ${st.remainingMinutes}m"
                PrintMonitorService.update(
                    app,
                    title = st.subtaskName.ifBlank { "Printing  列印中" },
                    text = text,
                    progress = st.progressPercent,
                    paused = st.gcodeState == GcodeState.PAUSE,
                )
            }
            else -> PrintMonitorService.stop(app)
        }
    }

    /** Connect straight to the printer over LAN (TLS MQTT 8883, no cloud/relay). */
    fun connectViaLan(ip: String, accessCode: String, serial: String) {
        if (ip.isBlank() || accessCode.isBlank() || serial.isBlank()) {
            _message.value = "Enter IP, access code, serial  請填 IP、存取碼、序號"; return
        }
        viewModelScope.launch {
            startStream(LanMqttTransport(ip, accessCode, serial), "LAN")
            if (_connected.value) {
                repo.savePrinter(
                    com.bambuprinterlan.core.model.Printer(serial = serial, name = serial, ip = ip, accessCode = accessCode)
                )
            }
        }
    }

    /** Save a printer locally without connecting (build a multi-printer list). */
    fun savePrinterManually(name: String, ip: String, accessCode: String, serial: String) {
        if (serial.isBlank()) { _message.value = "Enter a serial  請輸入序號"; return }
        viewModelScope.launch {
            repo.savePrinter(com.bambuprinterlan.core.model.Printer(
                serial = serial, name = name.ifBlank { serial }, ip = ip, accessCode = accessCode))
            _message.value = "Saved printer  已儲存打印機"
        }
    }

    // ---- relay printer registry --------------------------------------------
    fun refreshRelayPrinters() {
        viewModelScope.launch {
            val baseUrl = repo.getString("relay_base_url").first()
            if (baseUrl.isBlank()) { _message.value = "Set relay URL in Settings  喺設定填中繼網址"; return@launch }
            val token = repo.getString("relay_token").first()
            _relayPrinters.value = relayApi.list(baseUrl, token)
        }
    }

    fun addRelayPrinter(serial: String, ip: String, accessCode: String, name: String) {
        if (serial.isBlank() || ip.isBlank() || accessCode.isBlank()) {
            _message.value = "Need serial, IP, access code  需要序號、IP、存取碼"; return
        }
        viewModelScope.launch {
            val baseUrl = repo.getString("relay_base_url").first()
            if (baseUrl.isBlank()) { _message.value = "Set relay URL in Settings  喺設定填中繼網址"; return@launch }
            val token = repo.getString("relay_token").first()
            val ok = relayApi.add(baseUrl, token, serial, ip, accessCode, name)
            _message.value = if (ok) "Added to relay  已加入中繼" else "Add to relay failed  加入失敗"
            if (ok) _relayPrinters.value = relayApi.list(baseUrl, token)
        }
    }

    fun removeRelayPrinter(serial: String) {
        viewModelScope.launch {
            val baseUrl = repo.getString("relay_base_url").first()
            val token = repo.getString("relay_token").first()
            if (relayApi.remove(baseUrl, token, serial)) _relayPrinters.value = relayApi.list(baseUrl, token)
        }
    }

    fun removeSavedPrinter(printer: com.bambuprinterlan.core.model.Printer) {
        viewModelScope.launch { repo.removePrinter(printer.serial, printer.ip) }
    }

    private suspend fun startStream(t: DeviceTransport, label: String) {
        disconnect()
        runCatching {
            transport = t
            t.connect()
            _connected.value = true
            t.pushAll()
            lastState = null
            streamJob = viewModelScope.launch {
                runCatching {
                    t.status().collect { st ->
                        _status.value = st
                        CommandBus.publishStatus(st)
                        maybeNotify(st)
                        updateNotification(st)
                    }
                }.onFailure {
                    _message.value =
                        "Lost connection. Check the printer is on and on the same Wi-Fi, then reconnect.  連線中斷，請確認打印機已開並同一 Wi-Fi，再重新連線。"
                    _connected.value = false
                }
            }
            _message.value = "Connected ($label)  已連線"
        }.onFailure {
            _message.value = "$label connect failed  連線失敗: ${it.message ?: ""}"
            _connected.value = false
        }
    }

    fun disconnect() {
        streamJob?.cancel(); streamJob = null
        val t = transport; transport = null
        viewModelScope.launch { runCatching { t?.disconnect() } }
        _connected.value = false
        PrintMonitorService.stop(getApplication())
    }

    fun send(command: Command) {
        val t = transport ?: run { _message.value = "Not connected  未連線"; return }
        viewModelScope.launch { runCatching { t.send(command) } }
    }

    /** Upload a sliced .3mf then start it (LAN: FTPS to SD; relay: multipart). */
    fun sendPrint(uri: Uri) {
        val t = transport ?: run { _message.value = "Not connected  未連線"; return }
        viewModelScope.launch {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read file")
                var name = displayName(uri)
                if (!name.endsWith(".3mf", ignoreCase = true)) name += ".3mf"
                _message.value = "Uploading $name…  上載中…"
                t.upload(name, bytes)
                t.send(
                    Command.ProjectFile(
                        fileName = "Metadata/plate_1.gcode",
                        url = "ftp:///$name",
                        subtaskName = name.removeSuffix(".3mf"),
                        amsMapping = AmsMappingStore.mapping.value,
                    )
                )
                _message.value = "Print started  已開始列印"
            }.onFailure {
                _message.value = "Send failed  傳送失敗: ${it.message ?: ""}"
            }
        }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "print.3mf"
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i)
            }
        }
        return name
    }

    /** Fire Discord/Home Assistant notifications on print state transitions. */
    private suspend fun maybeNotify(st: DeviceStatus) {
        val prev = lastState
        lastState = st.gcodeState
        if (prev == null || prev == st.gcodeState) return
        when (st.gcodeState) {
            GcodeState.FINISH -> {
                PrintHistoryStore.add(st.subtaskName, "finished", System.currentTimeMillis())
                fireEvent("Print finished  列印完成", st.subtaskName, "project", DiscordClient.Category.PROJECT)
            }
            GcodeState.FAILED -> {
                PrintHistoryStore.add(st.subtaskName, "failed", System.currentTimeMillis())
                fireEvent("Print failed  列印失敗", st.subtaskName, "errors", DiscordClient.Category.ERROR)
            }
            else -> {}
        }
    }

    private suspend fun fireEvent(
        title: String, detail: String, category: String, discordCategory: DiscordClient.Category,
    ) {
        EventNotifier.fire(getApplication(), title, detail, category, discordCategory)
    }

    fun clearMessage() { _message.value = null }

    override fun onCleared() { disconnect() }
}
