package net.ccbluex.discordipc

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@JvmRecord
internal data class DiscordIpcFrame(val opcode: Opcode, val payload: JsonObject) {

    fun encode(): ByteBuffer {
        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
        require(payloadBytes.size <= MAX_PAYLOAD_SIZE) {
            "Discord IPC payload exceeds the $MAX_PAYLOAD_SIZE byte limit"
        }

        return ByteBuffer.allocate(HEADER_SIZE + payloadBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode.value)
            .putInt(payloadBytes.size)
            .put(payloadBytes)
            .flip()
    }

    enum class Opcode(val value: Int) {
        HANDSHAKE(0),
        FRAME(1),
        CLOSE(2),
        PING(3),
        PONG(4);

        companion object {
            @JvmStatic
            fun fromValue(value: Int) = entries.firstOrNull { it.value == value }
                ?: throw IOException("Unknown Discord IPC opcode: $value")
        }
    }

    @JvmRecord
    data class Header(val opcode: Opcode, val payloadSize: Int)

    companion object {
        const val HEADER_SIZE = Int.SIZE_BYTES * 2
        const val MAX_PAYLOAD_SIZE = 1024 * 1024

        @JvmStatic
        fun decodeHeader(header: ByteBuffer): Header {
            require(header.remaining() == HEADER_SIZE) { "Discord IPC header must contain $HEADER_SIZE bytes" }
            header.order(ByteOrder.LITTLE_ENDIAN)
            val opcode = Opcode.fromValue(header.getInt())
            val payloadSize = header.getInt()
            if (payloadSize !in 0..MAX_PAYLOAD_SIZE) {
                throw IOException("Invalid Discord IPC payload size: $payloadSize")
            }
            return Header(opcode, payloadSize)
        }

        @JvmStatic
        fun decode(opcode: Opcode, payload: ByteBuffer): DiscordIpcFrame {
            val payloadBytes = ByteArray(payload.remaining())
            payload.get(payloadBytes)
            val payload = try {
                JsonParser.parseString(payloadBytes.toString(Charsets.UTF_8)).asJsonObject
            } catch (exception: RuntimeException) {
                throw IOException("Invalid Discord IPC JSON payload", exception)
            }

            return DiscordIpcFrame(opcode, payload)
        }
    }
}
