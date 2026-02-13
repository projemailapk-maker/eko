package com.carpetqr.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CsvImportActivity : AppCompatActivity() {

    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        importCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_csv_import)

        val btnPick = findViewById<Button>(R.id.btnPickCsv)
        val txtStatus = findViewById<TextView>(R.id.txtImportStatus)

        btnPick.setOnClickListener {
            txtStatus.text = "Dosya seçiliyor..."
            pickCsv.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel"))
        }
    }

    private fun importCsv(uri: Uri) {
        val txtStatus = findViewById<TextView>(R.id.txtImportStatus)
        txtStatus.text = "Okunuyor..."

        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: run {
                    txtStatus.text = "Dosya okunamadı"
                    return
                }

            val lines = content
                .split("\n")
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }

            if (lines.isEmpty()) {
                txtStatus.text = "Boş dosya"
                return
            }

            // Header: #;ID;CINS;CARPET_NAME;QR_TEXT;...;IMAGE_URL
            val header = lines.first().trim()
            if (!header.contains("ID") || !header.contains("IMAGE_URL")) {
                txtStatus.text = "CSV başlık satırı beklenenden farklı"
                return
            }

            val db = FirebaseFirestore.getInstance()
            var successCount = 0
            var failCount = 0

            // Basit batch yaklaşımı (500 sınırı). Bu örnek dosyada 20 kayıt var.
            var batch = db.batch()
            var inBatch = 0

            fun commitBatch(onDone: () -> Unit) {
                if (inBatch == 0) {
                    onDone()
                    return
                }
                batch.commit()
                    .addOnSuccessListener { onDone() }
                    .addOnFailureListener { e ->
                        Log.e("CarpetQR", "Batch commit failed", e)
                        Toast.makeText(this, "Yükleme hatası: ${e.message}", Toast.LENGTH_LONG).show()
                        onDone()
                    }
            }

            // skip header
            val dataLines = lines.drop(1)
            for (line in dataLines) {
                val cols = line.split(';')
                // Minimum kolon sayısı (ornek dosyaya göre): 15
                if (cols.size < 15) {
                    failCount++
                    continue
                }

                val docId = cols[1].trim()
                val cins = cols[2].trim()
                val carpetName = cols[3].trim()
                val qrText = cols[4].trim()
                val imageUrlRaw = cols[14].trim()

                if (docId.isBlank()) {
                    failCount++
                    continue
                }

                val imageUrl = cleanUrl(imageUrlRaw)

                val data = hashMapOf(
                    // Mevcut çalışan ekranının okuduğu alanlar
                    "code" to docId,
                    "name" to carpetName,
                    "model" to "",
                    "patternNo" to "",
                    "imageUrl" to imageUrl,
                    // CSV'den ekstra alanlar
                    "cins" to cins,
                    "qrText" to qrText
                )

                val ref = db.collection("carpets").document(docId)
                batch.set(ref, data, SetOptions.merge())
                inBatch++

                if (inBatch >= 450) {
                    commitBatch {
                        batch = db.batch()
                        inBatch = 0
                    }
                }

                successCount++
            }

            commitBatch {
                txtStatus.text = "Yüklendi. Başarılı: $successCount, Hatalı: $failCount"
            }

        } catch (e: Exception) {
            Log.e("CarpetQR", "CSV import failed", e)
            txtStatus.text = "Hata: ${e.message}"
        }
    }

    private fun cleanUrl(raw: String): String {
        // CSV'de """https://...""" şeklinde gelebiliyor
        var s = raw.trim()
        if (s.startsWith("\"\"\"")) s = s.removePrefix("\"\"\"")
        if (s.endsWith("\"\"\"")) s = s.removeSuffix("\"\"\"")
        s = s.trim().trim('"')
        return s
    }
}
