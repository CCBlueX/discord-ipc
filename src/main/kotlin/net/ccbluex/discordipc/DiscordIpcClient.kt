package net.ccbluex.discordipc

import com.google.gson.JsonObject
import net.ccbluex.discordipc.DiscordIpcFrame.Opcode
import org.apache.logging.log4j.LogManager
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOGGER = LogManager.getLogger(DiscordIpcClient::class.java)

class DiscordIpcClient private constructor(
    private val applicationId: Long,
    private val readerExecutor: ExecutorService,
    private val pipePaths: () -> List<String>,
    private val transportFactory: DiscordIpcTransport.Factory,
) : Closeable {

    @JvmOverloads
    constructor(
        applicationId: Long,
        platform: DiscordIpcPlatform = DiscordIpcPlatform.current(),
        readerExecutor: ExecutorService = createReaderExecutor(),
    ) : this(
        applicationId = applicationId,
        readerExecutor = readerExecutor,
        pipePaths = { DiscordIpcPipeLocator.locate(platform) },
        transportFactory = platform.transportFactory(),
    )

    internal constructor(
        applicationId: Long,
        platform: DiscordIpcPlatform = DiscordIpcPlatform.current(),
        readerExecutor: ExecutorService = createReaderExecutor(),
        pipePaths: () -> List<String> = { DiscordIpcPipeLocator.locate(platform) },
        transportFactory: DiscordIpcTransport.Factory = platform.transportFactory(),
    ) : this(
        applicationId = applicationId,
        readerExecutor = readerExecutor,
        pipePaths = pipePaths,
        transportFactory = transportFactory,
    )

    private val stateLock = ReentrantLock()

    @Volatile
    var state = State.DISCONNECTED
        private set

    private var transport: DiscordIpcTransport? = null
    private var readerTask: Future<*>? = null
    private var lastActivityJson: String? = null

    fun connect() {
        stateLock.withLock {
            check(state != State.CLOSED) { "Discord IPC client is closed" }
            if (state == State.CONNECTED || state == State.CONNECTING) {
                return
            }
            state = State.CONNECTING
        }

        var lastFailure: Throwable? = null
        for (path in pipePaths()) {
            val candidate = try {
                transportFactory.open(Path.of(path))
            } catch (exception: Exception) {
                lastFailure = exception
                continue
            }

            try {
                handshake(candidate)
                stateLock.withLock {
                    check(state != State.CLOSED) { "Discord IPC client was closed while connecting" }
                    transport = candidate
                    lastActivityJson = null
                    state = State.CONNECTED
                    startReader(candidate)
                }
                return
            } catch (exception: Exception) {
                lastFailure = exception
                candidate.closeSafely()
            }
        }

        stateLock.withLock {
            if (state != State.CLOSED) {
                state = State.DISCONNECTED
            }
        }
        throw NoDiscordClientException(lastFailure)
    }

    fun sendActivity(activity: DiscordActivity) {
        val activityJson = activity.toJson()
        val serializedActivity = activityJson.toString()
        val currentTransport = stateLock.withLock {
            check(state == State.CONNECTED) { "Discord IPC client is not connected" }
            if (serializedActivity == lastActivityJson) {
                return
            }
            checkNotNull(transport)
        }

        val payload = JsonObject().apply {
            addProperty("cmd", "SET_ACTIVITY")
            add("args", JsonObject().apply {
                addProperty("pid", ProcessHandle.current().pid())
                add("activity", activityJson)
            })
            addProperty("nonce", UUID.randomUUID().toString())
        }

        try {
            currentTransport.write(DiscordIpcFrame(Opcode.FRAME, payload))
            stateLock.withLock {
                if (transport === currentTransport && state == State.CONNECTED) {
                    lastActivityJson = serializedActivity
                }
            }
        } catch (exception: Exception) {
            disconnect(currentTransport)
            throw exception
        }
    }

    override fun close() {
        val currentTransport = stateLock.withLock {
            if (state == State.CLOSED) {
                return
            }
            state = State.CLOSED
            lastActivityJson = null
            transport.also { transport = null }
        }

        readerTask?.cancel(true)
        readerTask = null
        if (currentTransport != null) {
            runCatching {
                currentTransport.write(DiscordIpcFrame(Opcode.CLOSE, JsonObject()))
            }
            currentTransport.closeSafely()
        }
        readerExecutor.shutdownNow()
    }

    private fun handshake(candidate: DiscordIpcTransport) {
        val payload = JsonObject().apply {
            addProperty("v", PROTOCOL_VERSION)
            addProperty("client_id", applicationId.toString())
        }
        candidate.write(DiscordIpcFrame(Opcode.HANDSHAKE, payload))

        val response = candidate.read()
        if (response.opcode == Opcode.CLOSE) {
            throw IOException(response.payload.errorMessage("Discord rejected the IPC handshake"))
        }
        if (response.opcode != Opcode.FRAME || response.payload.string("cmd") != "DISPATCH" ||
            response.payload.string("evt") != "READY") {
            throw IOException("Unexpected Discord IPC handshake response")
        }
    }

    private fun startReader(currentTransport: DiscordIpcTransport) {
        readerTask?.cancel(true)
        readerTask = readerExecutor.submit {
            try {
                while (!Thread.currentThread().isInterrupted && currentTransport.isOpen) {
                    val frame = currentTransport.read()
                    when (frame.opcode) {
                        Opcode.PING -> currentTransport.write(DiscordIpcFrame(Opcode.PONG, frame.payload))
                        Opcode.CLOSE -> throw IOException(
                            frame.payload.errorMessage("Discord closed the IPC connection")
                        )
                        Opcode.FRAME -> handleFrame(frame.payload)
                        Opcode.PONG -> Unit
                        Opcode.HANDSHAKE -> throw IOException("Unexpected Discord IPC handshake frame")
                    }
                }
            } catch (exception: Exception) {
                if (state != State.CLOSED && transport === currentTransport) {
                    LOGGER.warn("Discord IPC connection closed: ${exception.message}", exception)
                    disconnect(currentTransport)
                }
            }
        }
    }

    private fun handleFrame(payload: JsonObject) {
        if (payload.string("evt") == "ERROR") {
            val error = payload.getAsJsonObject("data")
            LOGGER.warn("Discord IPC error: ${error?.errorMessage("Unknown error")}")
        }
    }

    private fun disconnect(currentTransport: DiscordIpcTransport) {
        val shouldClose = stateLock.withLock {
            if (transport !== currentTransport) {
                false
            } else {
                transport = null
                lastActivityJson = null
                if (state != State.CLOSED) {
                    state = State.DISCONNECTED
                }
                true
            }
        }

        if (shouldClose) {
            currentTransport.closeSafely()
        }
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CLOSED,
    }

    private companion object {
        const val PROTOCOL_VERSION = 1
    }
}

private fun createReaderExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "Discord IPC").apply {
        isDaemon = true
    }
}

class NoDiscordClientException(cause: Throwable? = null) :
    IOException("No Discord IPC client is available", cause)

private fun DiscordIpcPlatform.transportFactory() = DiscordIpcTransport.Factory { path ->
    when (this) {
        DiscordIpcPlatform.WINDOWS -> WindowsDiscordIpcTransport(path)
        DiscordIpcPlatform.UNIX -> UnixDiscordIpcTransport(path)
        DiscordIpcPlatform.UNSUPPORTED ->
            throw IOException("Unsupported Discord IPC platform: $this")
    }
}

private fun DiscordIpcTransport.closeSafely() {
    runCatching(this::close).onFailure { exception ->
        LOGGER.debug("Failed to close Discord IPC transport", exception)
    }
}

private fun JsonObject.string(name: String): String? = get(name)
    ?.takeUnless { it.isJsonNull }
    ?.takeIf { it.isJsonPrimitive }
    ?.asString

private fun JsonObject.errorMessage(fallback: String): String {
    val data = getAsJsonObject("data") ?: this
    val code = data.get("code")?.takeIf { it.isJsonPrimitive }?.asInt
    val message = data.string("message") ?: fallback
    return if (code == null) message else "$message (code $code)"
}
