package com.example.virtualandroid.vm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

class VmClient(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onState(state: String, detail: String)
        fun onLog(line: String)
    }

    private var service: IVmService? = null

    private val callback = object : IVmCallback.Stub() {
        override fun onStateChanged(state: String, detail: String) {
            listener.onState(state, detail)
        }

        override fun onLogLine(line: String) {
            listener.onLog(line)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IVmService.Stub.asInterface(binder)
            service?.registerCallback(callback)
            listener.onConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            listener.onDisconnected()
        }
    }

    fun bind() {
        context.bindService(
            Intent(context, VmService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun unbind() {
        runCatching { service?.unregisterCallback(callback) }
        service = null
        runCatching { context.unbindService(connection) }
    }

    fun startP1(memoryMiB: Int, vcpus: Int) {
        service?.startP1Guest(memoryMiB, vcpus)
            ?: listener.onLog("VM service is not connected")
    }

    fun startP2(memoryMiB: Int, vcpus: Int) {
        service?.startP2Guest(memoryMiB, vcpus)
            ?: listener.onLog("VM service is not connected")
    }

    fun startP3(memoryMiB: Int, vcpus: Int) {
        service?.startP3Guest(memoryMiB, vcpus)
            ?: listener.onLog("VM service is not connected")
    }

    fun stop() {
        service?.stopVm() ?: listener.onLog("VM service is not connected")
    }
}
