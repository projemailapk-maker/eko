package com.carpetqr.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.math.abs

class QrScanActivity : AppCompatActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handled = false

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        findViewById<android.widget.Button>(R.id.btnClose)?.setOnClickListener {
            finish()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val previewView = findViewById<PreviewView>(R.id.previewView)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QrAnalyzer { raw ->
                        val parsed = parseQr(raw)
                        if (parsed != null) {
                            handled = true
                            val result = Intent().putExtra(EXTRA_CARPET_ID, parsed)
                            setResult(RESULT_OK, result)
                            finish()
                        } else {
                            Toast.makeText(this, "QR formatı tanınmadı: $raw", Toast.LENGTH_SHORT).show()
                        }
                    })
                }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, analyzer)
            } catch (e: Exception) {
                Log.e("CarpetQR", "Camera bind failed", e)
                Toast.makeText(this, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun parseQr(raw: String): String? {
        // Beklenen: <CINS>:<id> (ör. HALI:buhari-...)
        val s = sanitize(raw)
        val parts = s.split(':', limit = 2)
        if (parts.size == 2) {
            val cins = parts[0].trim()
            val id = sanitize(parts[1])
            if (cins.isNotBlank() && id.isNotBlank()) return id
        }
        // Bazı durumlarda direkt ID gelebilir
        if (s.isNotBlank() && !s.contains(' ')) return sanitize(s)
        return null
    }

    private fun sanitize(raw: String): String {
        var s = raw.trim()
        s = s.trim('"', '\'', '\uFEFF')
        s = s.replace("\u200B", "").replace("\u200E", "").replace("\u200F", "")
        s = s.replace("\n", "").replace("\r", "")
        return s.trim()
    }

    private inner class QrAnalyzer(
        private val onQr: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()
        private var lastSeen: String? = null
        private var lastSeenTs: Long = 0

        override fun analyze(imageProxy: ImageProxy) {
            if (handled) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes
                        .firstOrNull { it.valueType == Barcode.TYPE_TEXT || it.valueType == Barcode.TYPE_URL || it.valueType == Barcode.TYPE_UNKNOWN }
                        ?.rawValue
                        ?.trim()
                        .orEmpty()

                    if (value.isNotBlank()) {
                        Log.d("CarpetQR", "QR raw=$value")
                        val now = System.currentTimeMillis()
                        val same = value == lastSeen
                        val within = abs(now - lastSeenTs) < 1200
                        if (!same || !within) {
                            lastSeen = value
                            lastSeenTs = now
                            onQr(value)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CarpetQR", "QR scan failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    companion object {
        const val EXTRA_CARPET_ID = "extra_carpet_id"
    }
}
