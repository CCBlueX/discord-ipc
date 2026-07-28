package net.ccbluex.discordipc

import com.google.gson.JsonObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscordIpcClientTest {

    @Test
    fun `connects sends distinct activities and responds to ping`() {
        val transport = FakeTransport().apply {
            incoming += DiscordIpcFrame(
                DiscordIpcFrame.Opcode.FRAME,
                JsonObject().apply {
                    addProperty("cmd", "DISPATCH")
                    addProperty("evt", "READY")
                    add("data", JsonObject().apply {
                        add("user", JsonObject())
                    })
                },
            )
        }
        val client = DiscordIpcClient(
            applicationId = 123L,
            platform = DiscordIpcPlatform.WINDOWS,
            pipePaths = { listOf("test-pipe") },
        ) { transport }

        client.connect()
        val activity = DiscordActivity(details = "Testing")
        client.sendActivity(activity)
        client.sendActivity(activity)
        transport.incoming += DiscordIpcFrame(
            DiscordIpcFrame.Opcode.PING,
            JsonObject().apply {
                addProperty("nonce", "ping")
            },
        )

        assertTrue(transport.pongWritten.await(2, TimeUnit.SECONDS))
        assertEquals(DiscordIpcClient.State.CONNECTED, client.state)
        assertEquals(1, transport.written.count { it.payload["cmd"]?.asString == "SET_ACTIVITY" })

        client.close()
    }

    private class FakeTransport : DiscordIpcTransport {
        val incoming = LinkedBlockingQueue<DiscordIpcFrame>()
        val written = CopyOnWriteArrayList<DiscordIpcFrame>()
        val pongWritten = java.util.concurrent.CountDownLatch(1)

        @Volatile
        override var isOpen = true
            private set

        override fun read(): DiscordIpcFrame = incoming.take()

        override fun write(frame: DiscordIpcFrame) {
            written += frame
            if (frame.opcode == DiscordIpcFrame.Opcode.PONG) {
                pongWritten.countDown()
            }
        }

        override fun close() {
            isOpen = false
        }
    }
}
