package com.example.virtualandroid.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Surface-backed RFB renderer and touch bridge for P3. */
class GuestDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback, RfbProtocol.Listener {
    interface Listener {
        fun onDisplayStatus(text: String)
        fun onFirstFrame(width: Int, height: Int)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val firstFrame = AtomicBoolean(false)
    @Volatile private var client: RfbProtocol? = null
    @Volatile private var bitmap: Bitmap? = null
    @Volatile private var listener: Listener? = null
    @Volatile private var desired = false

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    fun setListener(listener: Listener?) { this.listener = listener }

    fun connect() {
        desired = true
        firstFrame.set(false)
        scheduleConnectAttempt()
    }

    fun disconnect() {
        desired = false
        client?.close()
        client = null
        listener?.onDisplayStatus("display disconnected")
    }

    private fun scheduleConnectAttempt() {
        worker.execute {
            var attempts = 0
            while (desired && client == null && attempts < 90) {
                attempts++
                val candidate = RfbProtocol(port = 5901)
                client = candidate
                try {
                    listener?.onDisplayStatus("connecting to guest display (attempt $attempts)")
                    candidate.run(this, connectTimeoutMs = 750)
                } finally {
                    if (client === candidate) client = null
                }
                if (desired && !firstFrame.get()) Thread.sleep(500)
            }
            if (desired && !firstFrame.get()) {
                listener?.onDisplayStatus("guest display not reachable")
            }
        }
    }

    override fun onConnected(info: RfbProtocol.ServerInfo) {
        bitmap = Bitmap.createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888)
        listener?.onDisplayStatus("RFB connected: ${info.width}x${info.height} ${info.name}")
        requestFocus()
        render()
    }

    override fun onFrame(rect: RfbProtocol.FrameRect) {
        val b = bitmap ?: return
        if (rect.x + rect.width <= b.width && rect.y + rect.height <= b.height) {
            b.setPixels(rect.argb, 0, rect.width, rect.x, rect.y, rect.width, rect.height)
            if (firstFrame.compareAndSet(false, true)) listener?.onFirstFrame(b.width, b.height)
            render()
        }
    }

    override fun onDisconnected(cause: Throwable?) {
        if (desired && !firstFrame.get()) {
            listener?.onDisplayStatus("RFB retry: ${cause?.message ?: "closed"}")
        } else if (cause != null && desired) {
            listener?.onDisplayStatus("RFB disconnected: ${cause.message}")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = render()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = render()
    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun render() {
        if (!holder.surface.isValid) return
        val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
        try {
            canvas.drawColor(Color.BLACK)
            val b = bitmap ?: return
            val scale = minOf(canvas.width.toFloat() / b.width, canvas.height.toFloat() / b.height)
            val dw = b.width * scale
            val dh = b.height * scale
            val left = (canvas.width - dw) / 2f
            val top = (canvas.height - dh) / 2f
            canvas.drawBitmap(b, null, android.graphics.RectF(left, top, left + dw, top + dh), paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val b = bitmap ?: return true
        val scale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val dw = b.width * scale
        val dh = b.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        val gx = ((event.x - left) / scale).toInt().coerceIn(0, b.width - 1)
        val gy = ((event.y - top) / scale).toInt().coerceIn(0, b.height - 1)
        val mask = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            else -> return true
        }
        client?.sendPointer(gx, gy, mask)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keysym = androidKeyToKeysym(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        client?.sendKey(keysym, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val keysym = androidKeyToKeysym(keyCode, event) ?: return super.onKeyUp(keyCode, event)
        client?.sendKey(keysym, false)
        return true
    }

    private fun androidKeyToKeysym(keyCode: Int, event: KeyEvent?): Int? = when (keyCode) {
        KeyEvent.KEYCODE_ENTER -> 0xff0d
        KeyEvent.KEYCODE_DEL -> 0xff08
        KeyEvent.KEYCODE_TAB -> 0xff09
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> 0xff1b
        KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
        KeyEvent.KEYCODE_DPAD_UP -> 0xff52
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
        KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
        else -> event?.unicodeChar?.takeIf { it > 0 }
    }

    override fun onDetachedFromWindow() {
        disconnect()
        worker.shutdownNow()
        super.onDetachedFromWindow()
    }
}
