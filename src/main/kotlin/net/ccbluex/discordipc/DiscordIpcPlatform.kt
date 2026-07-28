package net.ccbluex.discordipc

enum class DiscordIpcPlatform {
    WINDOWS,
    UNIX,
    UNSUPPORTED;

    companion object {
        @JvmStatic
        fun current(): DiscordIpcPlatform {
            val osName = System.getProperty("os.name").lowercase()
            return when {
                osName.contains("win") -> WINDOWS
                osName.contains("mac") || osName.contains("darwin") || osName.contains("linux") -> UNIX
                else -> UNSUPPORTED
            }
        }
    }
}
