package com.bambuprinterlan.net.bambu

import com.bambuprinterlan.core.model.Command
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Encodes a [Command] into the Bambu request envelope:
 *   { "<category>": { "command": "...", "sequence_id": "N", <params...> } }
 * The same bytes are published over LAN MQTT, cloud MQTT, or POSTed to the relay.
 */
class CommandCodec {
    private val seq = AtomicLong(0)

    fun nextSequenceId(): String = seq.incrementAndGet().toString()

    fun encode(command: Command, sequenceId: String = nextSequenceId()): String {
        val inner = buildJsonObject {
            put("command", JsonPrimitive(command.command))
            put("sequence_id", JsonPrimitive(sequenceId))
            for ((k, v) in command.params) put(k, v.toJson())
        }
        val envelope = JsonObject(mapOf(command.category to inner))
        return envelope.toString()
    }

    private fun Any?.toJson(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is List<*> -> JsonArray(map { it.toJson() })
        else -> JsonPrimitive(toString())
    }
}
