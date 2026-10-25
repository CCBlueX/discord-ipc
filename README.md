# discord-ipc

A small Kotlin/JVM client for the [Discord IPC](https://discord.com/developers/docs/topics/rpc)
protocol. It talks to the Discord desktop app's local socket directly — no Discord SDK, no HTTP, no
OAuth, no bot token.

## Features

- Finds the socket itself: `\\.\pipe\discord-ipc-N` on Windows, `$XDG_RUNTIME_DIR`/`$TMPDIR`/`/tmp`
  (including snap and flatpak directories) on Linux/macOS
- Handshake, PING/PONG keep-alive and `SET_ACTIVITY` out of the box
- Identical activities are not re-sent
- Runtime dependencies are just gson and log4j-core

## Requirements

- JDK 17+
- Discord desktop app running on the same machine

## Install

The library is published to CCBlueX Maven:

```kotlin
repositories {
    maven("https://maven.ccbluex.net/releases")
}

dependencies {
    implementation("net.ccbluex:discord-ipc:1.0.0")
}
```

Versions are published from tags: pushing tag `v1.0.0` publishes `1.0.0`.

## Usage

```kotlin
import net.ccbluex.discordipc.DiscordActivity
import net.ccbluex.discordipc.DiscordIpcClient
import java.time.Instant

val client = DiscordIpcClient(applicationId = 123456789012345678L)

try {
    client.connect()
    client.sendActivity(
        DiscordActivity(
            details = "Playing Minecraft",
            state = "Singleplayer",
            startTimestamp = Instant.now(),
            largeImage = DiscordActivity.Image("logo", "My App"),
        ),
    )
} finally {
    client.close()
}
```

All calls are blocking; `sendActivity` may be called from any thread.

## Behaviour

- `connect()` probes every candidate socket and throws `NoDiscordClientException` (an `IOException`)
  when none works, or when Discord rejects the handshake.
- `sendActivity()` without a connection throws `IllegalStateException`.
- Any transport failure marks the client `DISCONNECTED`; reconnecting is the next `connect()` call.
  The client never retries on its own.
- `close()` is idempotent.

## Building

```sh
./gradlew build
./gradlew test
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
