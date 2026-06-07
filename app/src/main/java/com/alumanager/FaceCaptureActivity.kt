package com.alumanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.alumanager.databinding.ActivityFaceCaptureBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Aperçu caméra frontale en direct + capture pour la reconnaissance faciale. */
class FaceCaptureActivity : AppCompatActivity() {

    private lateinit var b: ActivityFaceCaptureBinding
    private var imageCapture: ImageCapture? = null
    private val clockFmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { b.clock.text = clockFmt.format(Date()); handler.postDelayed(this, 1000) }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else { Toast.makeText(this, "Caméra refusée", Toast.LENGTH_SHORT).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFaceCaptureBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.title.text = intent.getStringExtra("title") ?: "Pointage"
        b.btnClose.setOnClickListener { finish() }
        b.btnCapture.setOnClickListener { capture() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.previewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Erreur caméra : ${e.message}", Toast.LENGTH_LONG).show(); finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        val ic = imageCapture ?: return
        val dir = File(cacheDir, "faces"); dir.mkdirs()
        val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        b.btnCapture.isEnabled = false
        ic.takePicture(opts, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                setResult(RESULT_OK, intent.putExtra("path", file.absolutePath))
                finish()
            }
            override fun onError(exc: ImageCaptureException) {
                b.btnCapture.isEnabled = true
                Toast.makeText(this@FaceCaptureActivity, "Erreur capture : ${exc.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
