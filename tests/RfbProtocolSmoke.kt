import com.example.virtualandroid.display.RfbProtocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main() {
    val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val pointerSeen = CountDownLatch(1)
    var serverFailure: Throwable? = null
    thread(name = "fake-rfb") {
        try {
            server.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                output.writeBytes("RFB 003.008\n")
                output.flush()
                check(String(input.readNBytes(12), Charsets.US_ASCII) == "RFB 003.008\n")
                output.write(byteArrayOf(1, 1))
                output.flush()
                check(input.readUnsignedByte() == 1)
                output.writeInt(0)
                output.flush()
                check(input.readUnsignedByte() == 1)

                output.writeShort(2)
                output.writeShort(1)
                output.write(ByteArray(16))
                val name = "fake-qemu".toByteArray()
                output.writeInt(name.size)
                output.write(name)
                output.flush()

                check(input.readNBytes(20).size == 20) // SetPixelFormat
                check(input.readNBytes(8).size == 8)  // SetEncodings
                val request = input.readNBytes(10)
                check(request.size == 10 && request[0].toInt() == 3)

                output.writeByte(0) // FramebufferUpdate
                output.writeByte(0)
                output.writeShort(1)
                output.writeShort(0)
                output.writeShort(0)
                output.writeShort(2)
                output.writeShort(1)
                output.writeInt(0) // Raw
                output.write(byteArrayOf(0, 0, -1, 0, 0, -1, 0, 0)) // red, green
                output.flush()

                val pointer = input.readNBytes(6)
                check(pointer.size == 6 && pointer[0].toInt() == 5 && pointer[1].toInt() == 1)
                pointerSeen.countDown()
            }
        } catch (t: Throwable) {
            serverFailure = t
        }
    }

    val client = RfbProtocol("127.0.0.1", server.localPort)
    var frameOk = false
    client.run(object : RfbProtocol.Listener {
        override fun onConnected(info: RfbProtocol.ServerInfo) {
            check(info.width == 2 && info.height == 1 && info.name == "fake-qemu")
        }

        override fun onFrame(rect: RfbProtocol.FrameRect) {
            check(rect.argb.contentEquals(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt())))
            frameOk = true
            client.sendPointer(1, 0, 1)
            client.close()
        }

        override fun onDisconnected(cause: Throwable?) = Unit
    })
    check(frameOk)
    check(pointerSeen.await(2, TimeUnit.SECONDS))
    server.close()
    serverFailure?.let { throw it }
    println("RFB integration smoke: OK")
}
