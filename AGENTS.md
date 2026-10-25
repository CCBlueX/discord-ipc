# AGENTS.md

Kotlin/JVM client for the Discord IPC socket protocol. It talks to the Discord desktop app's
named pipe / unix socket directly — no Discord SDK, no HTTP, no OAuth.

## Commands

- Gradle wrapper only, JDK 17 toolchain: `./gradlew build`, `./gradlew test`
- Scope test runs to what you touched: `./gradlew test --tests 'net.ccbluex.discordipc.DiscordIpcFrameTest'`
- `./gradlew publish` needs `MAVEN_TOKEN_NAME` / `MAVEN_TOKEN_SECRET`; local work never does.

## Layout

- `DiscordIpcClient` — the public entry point: pipe probing, handshake, reader thread, PING→PONG,
  `SET_ACTIVITY`. Identical activities are deduplicated by their serialized JSON.
- `DiscordIpcTransport` — `internal`: Windows (`FileChannel` on `\\.\pipe\discord-ipc-N`, 50 ms poll
  loop because a blocking pipe read cannot be interrupted) and Unix (`AF_UNIX` `SocketChannel`).
- `DiscordIpcPipeLocator` — `internal`: candidate paths per platform (`XDG_RUNTIME_DIR`/`TMPDIR`/
  `TMP`/`TEMP`, snap/flatpak subdirs, indices 0–9).
- `DiscordIpcFrame` — 8-byte little-endian header (`opcode`, `payloadSize`) + UTF-8 JSON, 1 MiB cap.
- `DiscordActivity` — activity model; `toJson()` is the only place Discord's wire field names appear.
- `DiscordIpcPlatform` — OS detection, with an injectable `UNSUPPORTED` for tests.

## Conventions

- Public surface stays `DiscordIpcClient`, `DiscordActivity`, `DiscordIpcPlatform`,
  `NoDiscordClientException`. Everything else is `internal`; do not widen it.
- Java consumers are supported: keep `@JvmRecord` on data classes, `@JvmOverloads` on the public
  constructor, `@JvmStatic` on companion factories.
- Dependencies are intentionally gson + log4j-core (+ `kotlin.test`). Do not add a Discord, SDK,
  JSON-mapper or HTTP library.
- Fail loudly, do not retry: any transport failure goes through `disconnect()` → `DISCONNECTED`,
  and reconnecting is the caller's next `connect()`. Probing exhausted → `NoDiscordClientException`.
- Mutable state is only touched under `stateLock`, and the reader thread re-checks
  `transport === currentTransport` — it does not own the connection forever.
- Tests fake the transport (`FakeTransport`) rather than mocking the client. The real transports are
  not unit-tested; they need a live Discord client.

## Gotchas

- `tasks.withType<Jar>` packages the repo-root `LICENSE`, and `publishing` declares `sourcesJar` /
  `javadocJar`. Removing either file or task breaks `build` / `publish`.
- Licensed Apache-2.0 (see `LICENSE`). Contributions are accepted under the same terms; keep the
  copyright notice in place.
