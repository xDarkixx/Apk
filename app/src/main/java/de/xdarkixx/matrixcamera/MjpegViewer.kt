package de.xdarkixx.matrixcamera

import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

class MjpegViewer(private val host: String, private val port: Int, private val onFrame: (android.graphics.Bitmap) -> Unit) {
    fun start() {
        Thread {
            try {
                val conn = URL("http://$host:$port/stream").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 0; conn.doInput = true; conn.connect()
                val input = conn.inputStream
                val buffer = ByteArray(64 * 1024); val data = java.io.ByteArrayOutputStream()
                var prev = -1
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    for (i in 0 until n) {
                        val v = buffer[i].toInt() and 255
                        if (prev == 0xFF && v == 0xD8) data.reset()
                        if (prev == 0xFF && v == 0xD8) { data.write(0xFF); data.write(0xD8) }
                        else if (data.size() > 0) data.write(v)
                        if (prev == 0xFF && v == 0xD9) {
                            val bytes = data.toByteArray(); val bmp = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                            if (bmp != null) onFrame(bmp); data.reset()
                        }
                        prev = v
                    }
                }
                input.close(); conn.disconnect()
            } catch (_: Exception) { }
        }.start()
    }
}
