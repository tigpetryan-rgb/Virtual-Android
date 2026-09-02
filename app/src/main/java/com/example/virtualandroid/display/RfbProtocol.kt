package com.example.virtualandroid.display

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal RFB 3.8 client for QEMU's loopback VNC server.
 *
 * Scope is deliberately narrow: security type None, 32-bit true-colour raw
 * framebuffer encoding, framebuffer updates, pointer events and key events.
 * This keeps P3 dependency-free and makes protocol failures observable.
 */
class RfbProtocol(
    private val host: String = "127.0.0.1",
    private val port: Int = 5901,
) : AutoCloseable {
    data class ServerInfo(val width: Int, val height: Int, val name: String)
    data class FrameRect(val x: Int, val y: Int, val width: Int, val height: Int, val argb: IntArray)

    interface Listener {
        fun onConnected(info: ServerInfo)
        fun onFrame(rect: FrameRect)
        fun onBell() = Unit
        fun onClipboard(text: String) = Unit
        fun onDisconnected(cause: Throwable?)
    }

    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    @Volatile var framebufferWidth: Int = 0
        private set
    @Volatile var framebufferHeight: Int = 0
        private set

    fun run(listener: Listener, connectTimeoutMs: Int = 2_000) {
        check(running.compareAndSet(false, true)) { "RFB client already running" }
        var terminalError: Throwable? = null
        try {
            val s = Socket()
            socket = s
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            input = BufferedInputStream(s.getInputStream(), 256 * 1024)
            output = BufferedOutputStream(s.getOutputStream(), 64 * 1024)

            val info = handshake()
            listener.onConnected(info)
            setPixelFormat()
            setEncodings()
            requestUpdate(incremental = false)

            while (running.get()) {
                when (readU8()) {
                    0 -> readFramebufferUpdate(listener)
                    2 -> listener.onBell()
                    3 -> listener.onClipboard(readServerCutText())
                    else -> error("Unsupported RFB server message")
                }
            }
        } catch (t: Throwable) {
            terminalError = t
        } finally {
            running.set(false)
            runCatching { socket?.close() }
            socket = null
            input = null
            output = null
            listener.onDisconnected(terminalError)
        }
    }

    private fun handshake(): ServerInfo {
        val serverVersion = readExact(12).toString(StandardCharsets.US_ASCII)
        require(serverVersion.startsWith("RFB 003.")) { "Unsupported RFB version: $serverVersion" }
        writeRaw("RFB 003.008\n".toByteArray(StandardCharsets.US_ASCII))
        flush()

        val count = readU8()
        if (count == 0) {
            val reason = readString32()
            error("RFB server rejected security negotiation: $reason")
        }
        val types = readExact(count)
        require(types.any { (it.toInt() and 0xff) == 1 }) {
            "QEMU VNC did not offer RFB security type None"
        }
        writeU8(1)
        flush()
        val securityResult = readU32()
        if (securityResult != 0L) {
            val reason = runCatching { readString32() }.getOrDefault("security failed")
            error("RFB security failed ($securityResult): $reason")
        }

        writeU8(1) // shared-flag
        flush()
        val width = readU16()
        val height = readU16()
        readExact(16) // server pixel format; replaced immediately below
        val nameLen = readU32().toInt()
        require(nameLen in 0..1_048_576) { "Invalid RFB desktop name length: $nameLen" }
        val name = readExact(nameLen).toString(StandardCharsets.UTF_8)
        framebufferWidth = width
        framebufferHeight = height
        return ServerInfo(width, height, name)
    }

    /** Request B,G,R,0 wire bytes: 32bpp little-endian true colour. */
    private fun setPixelFormat() {
        val out = output ?: error("RFB output closed")
        synchronized(out) {
            out.write(0) // SetPixelFormat
            out.write(byteArrayOf(0, 0, 0))
            out.write(32) // bits-per-pixel
            out.write(24) // depth
            out.write(0) // little endian
            out.write(1) // true colour
            writeU16Locked(out, 255)
            writeU16Locked(out, 255)
            writeU16Locked(out, 255)
            out.write(16) // red shift
            out.write(8)  // green shift
            out.write(0)  // blue shift
            out.write(byteArrayOf(0, 0, 0))
            out.flush()
        }
    }

    private fun setEncodings() {
        val out = output ?: error("RFB output closed")
        synchronized(out) {
            out.write(2) // SetEncodings
            out.write(0)
            writeU16Locked(out, 1)
            writeS32Locked(out, 0) // Raw only
            out.flush()
        }
    }

    fun requestUpdate(incremental: Boolean = true) {
        val w = framebufferWidth
        val h = framebufferHeight
        if (w <= 0 || h <= 0) return
        val out = output ?: return
        synchronized(out) {
            out.write(3)
            out.write(if (incremental) 1 else 0)
            writeU16Locked(out, 0)
            writeU16Locked(out, 0)
            writeU16Locked(out, w)
            writeU16Locked(out, h)
            out.flush()
        }
    }

    fun sendPointer(x: Int, y: Int, buttonMask: Int) {
        val w = framebufferWidth
        val h = framebufferHeight
        if (w <= 0 || h <= 0) return
        val out = output ?: return
        synchronized(out) {
            out.write(5)
            out.write(buttonMask and 0xff)
            writeU16Locked(out, x.coerceIn(0, w - 1))
            writeU16Locked(out, y.coerceIn(0, h - 1))
            out.flush()
        }
    }

    fun sendKey(keysym: Int, down: Boolean) {
        val out = output ?: return
        synchronized(out) {
            out.write(4)
            out.write(if (down) 1 else 0)
            out.write(byteArrayOf(0, 0))
            writeS32Locked(out, keysym)
            out.flush()
        }
    }

    private fun readFramebufferUpdate(listener: Listener) {
        readU8() // padding
        val rects = readU16()
        repeat(rects) {
            val x = readU16()
            val y = readU16()
            val w = readU16()
            val h = readU16()
            val encoding = readS32()
            require(encoding == 0) { "Unsupported RFB encoding $encoding (Raw expected)" }
            val pixelCount = Math.multiplyExact(w, h)
            require(pixelCount <= 16_777_216) { "Unreasonable RFB rectangle ${w}x$h" }
            val bytes = readExact(Math.multiplyExact(pixelCount, 4))
            val argb = IntArray(pixelCount)
            var j = 0
            for (i in 0 until pixelCount) {
                val b = bytes[j++].toInt() and 0xff
                val g = bytes[j++].toInt() and 0xff
                val r = bytes[j++].toInt() and 0xff
                j++ // unused high byte
                argb[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
            listener.onFrame(FrameRect(x, y, w, h, argb))
        }
        requestUpdate(incremental = true)
    }

    private fun readServerCutText(): String {
        readExact(3)
        val len = readU32().toInt()
        require(len in 0..4_194_304) { "Invalid RFB clipboard length: $len" }
        return readExact(len).toString(StandardCharsets.UTF_8)
    }

    override fun close() {
        running.set(false)
        runCatching { socket?.close() }
    }

    private fun readString32(): String {
        val len = readU32().toInt()
        require(len in 0..1_048_576) { "Invalid RFB string length: $len" }
        return readExact(len).toString(StandardCharsets.UTF_8)
    }

    private fun readU8(): Int {
        val v = input?.read() ?: -1
        if (v < 0) throw EOFException("RFB EOF")
        return v
    }

    private fun readU16(): Int = (readU8() shl 8) or readU8()
    private fun readU32(): Long = ((readU8().toLong() shl 24) or
        (readU8().toLong() shl 16) or (readU8().toLong() shl 8) or readU8().toLong()) and 0xffffffffL
    private fun readS32(): Int = readU32().toInt()

    private fun readExact(n: Int): ByteArray {
        val data = ByteArray(n)
        var off = 0
        val src = input ?: throw EOFException("RFB input closed")
        while (off < n) {
            val got = src.read(data, off, n - off)
            if (got < 0) throw EOFException("RFB EOF: wanted=$n got=$off")
            off += got
        }
        return data
    }

    private fun writeRaw(bytes: ByteArray) {
        val out = output ?: error("RFB output closed")
        synchronized(out) { out.write(bytes) }
    }
    private fun writeU8(v: Int) {
        val out = output ?: error("RFB output closed")
        synchronized(out) { out.write(v and 0xff) }
    }
    private fun flush() { output?.flush() }

    private fun writeU16Locked(out: BufferedOutputStream, v: Int) {
        out.write((v ushr 8) and 0xff)
        out.write(v and 0xff)
    }
    private fun writeS32Locked(out: BufferedOutputStream, v: Int) {
        out.write((v ushr 24) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write(v and 0xff)
    }
}
