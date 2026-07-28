package net.ccbluex.discordipc

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscordIpcPipeLocatorTest {

    @Test
    fun `locates Windows named pipes`() {
        val paths = DiscordIpcPipeLocator.locate(DiscordIpcPlatform.WINDOWS, emptyMap(), null) { false }

        assertEquals(10, paths.size)
        assertEquals("\\\\.\\pipe\\discord-ipc-0", paths.first())
    }

    @Test
    fun `locates standard and sandboxed Unix sockets`() {
        val runtimeDirectory = Path.of("build/test-discord-runtime").toAbsolutePath().normalize()
        val flatpakDirectory = runtimeDirectory.resolve("app/com.discordapp.Discord")
        val paths = DiscordIpcPipeLocator.locate(
            platform = DiscordIpcPlatform.UNIX,
            environment = mapOf("XDG_RUNTIME_DIR" to runtimeDirectory.toString()),
            javaTemporaryDirectory = Path.of("build/test-discord-tmp").toAbsolutePath().normalize().toString(),
            isDirectory = { it == flatpakDirectory },
        )

        assertTrue(runtimeDirectory.resolve("discord-ipc-0").toString() in paths)
        assertTrue(flatpakDirectory.resolve("discord-ipc-9").toString() in paths)
    }
}
