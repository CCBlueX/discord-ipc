package net.ccbluex.discordipc

import com.google.gson.JsonObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscordIpcFrameTest {

    @Test
    fun `round trips frame with little endian header`() {
        val frame = DiscordIpcFrame(
            DiscordIpcFrame.Opcode.FRAME,
            JsonObject().apply {
                addProperty("cmd", "SET_ACTIVITY")
            },
        )

        val buffer = frame.encode()
        val header = buffer.slice(0, DiscordIpcFrame.HEADER_SIZE)
        buffer.position(DiscordIpcFrame.HEADER_SIZE)
        val payload = buffer.slice()
        val (opcode, payloadSize) = DiscordIpcFrame.decodeHeader(header)

        assertEquals(DiscordIpcFrame.Opcode.FRAME, opcode)
        assertEquals(payload.remaining(), payloadSize)
        assertEquals(frame, DiscordIpcFrame.decode(opcode, payload))
    }

    @Test
    fun `rejects oversized payload`() {
        val buffer = ByteBuffer.allocate(DiscordIpcFrame.HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(DiscordIpcFrame.Opcode.FRAME.value)
            .putInt(DiscordIpcFrame.MAX_PAYLOAD_SIZE + 1)
            .flip()

        assertFailsWith<IOException> {
            DiscordIpcFrame.decodeHeader(buffer)
        }
    }
}
