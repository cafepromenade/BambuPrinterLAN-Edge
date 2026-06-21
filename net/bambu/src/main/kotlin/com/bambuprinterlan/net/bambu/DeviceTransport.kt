package com.bambuprinterlan.net.bambu

import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.DeviceStatus
import kotlinx.coroutines.flow.Flow

/**
 * One interface, three implementations (LAN-direct, cloud, relay). The UI and
 * DeviceRepository never know which transport is active — see protocol §2/§9.
 */
interface DeviceTransport {
    val serial: String

    /** Hot stream of merged status snapshots. */
    fun status(): Flow<DeviceStatus>

    suspend fun connect()
    suspend fun disconnect()

    /** Send a modeled command (codec produces the identical Bambu envelope). */
    suspend fun send(command: Command)

    /** Force a full state snapshot. */
    suspend fun pushAll() = send(Command.Pushall())

    /** Upload a sliced file (LAN: FTPS to SD; relay: multipart; cloud: presigned). */
    suspend fun upload(remoteName: String, bytes: ByteArray)
}
