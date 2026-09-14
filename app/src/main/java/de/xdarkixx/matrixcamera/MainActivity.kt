package de.xdarkixx.matrixcamera

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.os.StrictMode
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var server: MjpegServer
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().permitAll().build())
        server = MjpegServer(8080)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(0xFF05070A.toInt()) }
        preview = PreviewView(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        root.addView(preview)
        status = TextView(this).apply { setTextColor(0xFF00E5FF.toInt()); textSize = 16f; gravity = Gravity.CENTER; text = "Matrix Camera – bereit" }
        root.addView(status, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val ip = EditText(this).apply { hint = "Viewer-IP (z.B. 192.168.1.20)"; setTextColor(-1); setHintTextColor(0xFF777777.toInt()); inputType = 1 }
        root.addView(ip)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val camera = Button(this).apply { text = "KAMERA START" }
        val viewer = Button(this).apply { text = "VIEWER START" }
        row.addView(camera, LinearLayout.LayoutParams(0,-2,1f)); row.addView(viewer, LinearLayout.LayoutParams(0,-2,1f)); root.addView(row)
        setContentView(root)

        camera.setOnClickListener { startCamera() }
        viewer.setOnClickListener {
            val host = ip.text.toString().trim()
            if (host.isBlank()) { status.text = "Bitte IP-Adresse eingeben"; return@setOnClickListener }
            preview.visibility = android.view.View.VISIBLE
            status.text = "Verbinde mit $host:8080 …"
            Thread { MjpegViewer(host,8080) { bitmap -> runOnUiThread { preview.visibility = android.view.View.GONE; status.text = "Viewer verbunden: $host"; Toast.makeText(this,"Livebild aktiv",Toast.LENGTH_SHORT).show() } }.start() }.start()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            analysis.setAnalyzer(executor) { image ->
                val bitmap = image.toBitmap()
                server.updateFrame(bitmap)
                image.close()
            }
            provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysis)
            server.start()
            status.text = "KAMERA AKTIV – http://${localIp()}:8080"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun localIp(): String = try { NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.firstOrNull { !it.isLoopbackAddress && it.hostAddress.indexOf(':') < 0 }?.hostAddress ?: "<IP>" } catch (_: Exception) { "<IP>" }

    override fun onDestroy() { server.stop(); executor.shutdown(); super.onDestroy() }
}
