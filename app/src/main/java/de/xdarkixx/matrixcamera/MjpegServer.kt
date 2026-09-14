package de.xdarkixx.matrixcamera

import android.graphics.Bitmap
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

class MjpegServer(port: Int) : NanoHTTPD(port) {
    private val latest = AtomicReference<ByteArray?>(null)
    private var running = false

    fun updateFrame(bitmap: Bitmap) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        latest.set(out.toByteArray())
    }

    override fun start() { if (!running) { super.start(SOCKET_READ_TIMEOUT, false); running = true } }
    override fun stop() { running = false; super.stop() }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/") return newFixedLengthResponse(Response.Status.OK, "text/html", "<h2>Matrix Camera</h2><p>Live stream: /stream</p>")
        if (session.uri != "/stream") return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        val stream = object : InputStream() {
            private var current = ByteArray(0); private var pos = 0; private var sentFrame = -1L; private var frameNo = 0L
            override fun read(): Int { val b=ByteArray(1); return if (read(b,0,1)<0) -1 else b[0].toInt() and 255 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                while (running) {
                    if (pos >= current.size) {
                        val frame = latest.get()
                        if (frame == null || frameNo == sentFrame) { try { Thread.sleep(30) } catch (_: InterruptedException) { return -1 }; continue }
                        frameNo++; sentFrame = frameNo
                        current = buildPart(frame); pos = 0
                    }
                    val n = minOf(len, current.size-pos); System.arraycopy(current,pos,b,off,n); pos += n; return n
                }
                return -1
            }
            private fun buildPart(jpeg: ByteArray): ByteArray {
                val head = "--frame\\r\\nContent-Type: image/jpeg\\r\\nContent-Length: ${jpeg.size}\\r\\n\\r\\n".replace("\\\\r", "\\r").replace("\\\\n", "\\n").toByteArray()
                return head + jpeg + "\\r\\n".replace("\\\\r", "\\r").replace("\\\\n", "\\n").toByteArray()
            }
        }
        return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=frame", stream)
    }
}
