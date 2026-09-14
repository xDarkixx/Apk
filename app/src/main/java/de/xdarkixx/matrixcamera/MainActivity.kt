package de.xdarkixx.matrixcamera

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import org.webrtc.SurfaceViewRenderer

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var remote: SurfaceViewRenderer
    private var engine: WebRtcEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20); setBackgroundColor(0xFF05070A.toInt()) }
        val title = TextView(this).apply { text = "MATRIX CAMERA • INTERNET"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xFF00E5FF.toInt()) }
        status = TextView(this).apply { text = "Bereit – Gerät ausdrücklich koppeln"; gravity = Gravity.CENTER; setTextColor(-1) }
        remote = SurfaceViewRenderer(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val server = EditText(this).apply { hint = "wss://dein-server.example/signal"; setTextColor(-1); setHintTextColor(0xFF777777.toInt()) }
        val room = EditText(this).apply { hint = "Kopplungscode / Raum (mind. 8 Zeichen)"; setTextColor(-1); setHintTextColor(0xFF777777.toInt()) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val camera = Button(this).apply { text = "KAMERA" }
        val viewer = Button(this).apply { text = "VIEWER" }
        val stop = Button(this).apply { text = "STOP" }
        row.addView(camera, LinearLayout.LayoutParams(0,-2,1f)); row.addView(viewer, LinearLayout.LayoutParams(0,-2,1f)); row.addView(stop, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(title); root.addView(status); root.addView(remote); root.addView(server); root.addView(room); root.addView(row); setContentView(root)

        camera.setOnClickListener { if (cameraPermission()) start(server.text.toString(), room.text.toString(), true) }
        viewer.setOnClickListener { start(server.text.toString(), room.text.toString(), false) }
        stop.setOnClickListener { engine?.stop(); engine = null; status.text = "Gestoppt" }
    }

    private fun start(url: String, code: String, camera: Boolean) {
        if (url.isBlank() || code.length < 8) { status.text = "Server-URL und Kopplungscode nötig"; return }
        engine?.stop()
        status.text = if (camera) "Kamera wird verbunden …" else "Viewer wird verbunden …"
        engine = WebRtcEngine(this, url, code, camera, if (camera) null else remote) { status.text = it }
        engine?.start()
    }

    private fun cameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        status.text = "Kamera-Berechtigung erteilen und KAMERA erneut drücken"
        return false
    }

    override fun onDestroy() { engine?.stop(); super.onDestroy() }
}
