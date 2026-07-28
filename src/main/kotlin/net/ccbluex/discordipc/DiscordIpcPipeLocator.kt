package net.ccbluex.discordipc

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal object DiscordIpcPipeLocator {

    private val environmentVariables = arrayOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")
    private val sandboxSubdirectories = arrayOf("snap.discord", "app/com.discordapp.Discord")

    fun locate(platform: DiscordIpcPlatform = DiscordIpcPlatform.current()): List<String> = locate(
        platform = platform,
        environment = System.getenv(),
        javaTemporaryDirectory = System.getProperty("java.io.tmpdir"),
        isDirectory = Files::isDirectory,
    )

    internal fun locate(
        platform: DiscordIpcPlatform,
        environment: Map<String, String>,
        javaTemporaryDirectory: String?,
        isDirectory: (Path) -> Boolean,
    ): List<String> = when (platform) {
        DiscordIpcPlatform.WINDOWS -> List(MAX_PIPE_COUNT) { "\\\\.\\pipe\\discord-ipc-$it" }
        DiscordIpcPlatform.UNIX -> locateUnix(environment, javaTemporaryDirectory, isDirectory)
        DiscordIpcPlatform.UNSUPPORTED -> emptyList()
    }

    private fun locateUnix(
        environment: Map<String, String>,
        javaTemporaryDirectory: String?,
        isDirectory: (Path) -> Boolean,
    ): List<String> {
        val baseDirectories = LinkedHashSet<Path>()
        environmentVariables.mapNotNullTo(baseDirectories) { variable -> environment[variable].toPathOrNull() }
        javaTemporaryDirectory.toPathOrNull()?.let(baseDirectories::add)
        baseDirectories.add(Path.of("/tmp"))

        val pipeDirectories = LinkedHashSet<Path>()
        baseDirectories.forEach { baseDirectory ->
            pipeDirectories.add(baseDirectory)
            sandboxSubdirectories
                .map(baseDirectory::resolve)
                .filterTo(pipeDirectories, isDirectory)
        }

        return pipeDirectories.flatMap { directory ->
            List(MAX_PIPE_COUNT) { index -> directory.resolve("discord-ipc-$index").toString() }
        }
    }

    private fun String?.toPathOrNull(): Path? {
        if (this.isNullOrBlank()) {
            return null
        }

        return try {
            Path.of(this).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        }
    }

    private const val MAX_PIPE_COUNT = 10
}
