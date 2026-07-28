package net.ccbluex.discordipc

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface DiscordIpcTransport : Closeable {
    val isOpen: Boolean

    fun read(): DiscordIpcFrame

    fun write(frame: DiscordIpcFrame)

    fun interface Factory {
        fun open(path: Path): DiscordIpcTransport
    }
}

internal class WindowsDiscordIpcTransport(path: Path) : DiscordIpcTransport {

    private val closed = AtomicBoolean()
    private val writeLock = ReentrantLock()
    private val channel = FileChannel.open(path, READ_WRITE_OPTIONS)
    override val isOpen: Boolean
        get() = !closed.get()

    override fun read(): DiscordIpcFrame {
        val header = ByteBuffer.allocate(DiscordIpcFrame.HEADER_SIZE)
        readFully(header)
        val (opcode, payloadSize) = DiscordIpcFrame.decodeHeader(header.flip())
        val payload = ByteBuffer.allocate(payloadSize)
        readFully(payload)
        return DiscordIpcFrame.decode(opcode, payload.flip())
    }

    override fun write(frame: DiscordIpcFrame) = writeLock.withLock {
        ensureOpen()
        channel.writeFully(frame.encode())
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            channel.close()
        }
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw IOException("Discord IPC transport is closed")
        }
    }

    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            while (!closed.get() && channel.size() == 0L) {
                try {
                    // To prevent deadlock on blocking reading
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Discord IPC read interrupted")
                }
            }
            if (closed.get()) {
                throw EOFException("Discord IPC transport is closed")
            }
            if (channel.read(buffer) < 0) {
                throw EOFException("Discord IPC pipe closed before the frame was complete")
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L

        private val READ_WRITE_OPTIONS = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)
    }
}

internal class UnixDiscordIpcTransport(path: Path) : DiscordIpcTransport {

    private val closed = AtomicBoolean()
    private val writeLock = ReentrantLock()
    private val channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
        connect(UnixDomainSocketAddress.of(path))
        configureBlocking(true)
    }
    override val isOpen: Boolean
        get() = !closed.get() && channel.isOpen && channel.isConnected

    override fun read(): DiscordIpcFrame {
        val header = ByteBuffer.allocate(DiscordIpcFrame.HEADER_SIZE)
        channel.readFully(header)
        val (opcode, payloadSize) = DiscordIpcFrame.decodeHeader(header.flip())
        val payload = ByteBuffer.allocate(payloadSize)
        channel.readFully(payload)
        return DiscordIpcFrame.decode(opcode, payload.flip())
    }

    override fun write(frame: DiscordIpcFrame) = writeLock.withLock {
        ensureOpen()
        channel.writeFully(frame.encode())
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            channel.close()
        }
    }

    private fun ensureOpen() {
        if (!isOpen) {
            throw IOException("Discord IPC transport is closed")
        }
    }
}

private fun ReadableByteChannel.readFully(buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
        if (read(buffer) < 0) {
            throw EOFException("Discord IPC channel closed before the frame was complete")
        }
    }
}

private fun WritableByteChannel.writeFully(buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
        write(buffer)
    }
}
