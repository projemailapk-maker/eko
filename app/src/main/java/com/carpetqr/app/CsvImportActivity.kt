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
            val headerLine = lines.first().trim()
            if (!headerLine.contains("ID") || !headerLine.contains("IMAGE_URL")) {
                txtStatus.text = "CSV başlık satırı beklenenden farklı"
                return
            }

            val headers = headerLine.split(';').map { it.trim() }
            val idIndex = headers.indexOf("ID")
            if (idIndex == -1) {
                txtStatus.text = "CSV'de ID sütunu yok"
                return
            }

            val db = FirebaseFirestore.getInstance()
            var successCount = 0
            var failCount = 0

            // Batch (500 limit) - tek seferde commit edeceğiz (şimdilik küçük dosyalar için)
            val batch = db.batch()

            // skip header
            val dataLines = lines.drop(1)
            for (line in dataLines) {
                val cols = line.split(';')

                if (cols.size < headers.size) {
                    failCount++
                    continue
                }

                val docId = cols[idIndex].trim()
                if (docId.isBlank()) {
                    failCount++
                    continue
                }

                // 1) CSV başlıkları ile aynı field isimleriyle yaz
                val data = HashMap<String, Any?>()
                for (i in headers.indices) {
                    val key = headers[i]
                    if (key.isBlank()) continue
                    if (i >= cols.size) continue

                    var value: String = cols[i].trim()
                    if (key == "IMAGE_URL") {
                        value = cleanUrl(value)
                    }
                    // boş hücreleri null yaparak Firestore'u şişirmeyelim
                    data[key] = value.ifBlank { null }
                }

                // 2) Uyumluluk alanları (çalışan ekranı için)
                val carpetName = (data["CARPET_NAME"] as? String).orEmpty()
                val kalite = (data["KALITE"] as? String).orEmpty()
                val desen = (data["DESEN"] as? String).orEmpty()
                val imageUrl = (data["IMAGE_URL"] as? String).orEmpty()

                data["code"] = docId
                data["name"] = carpetName
                data["model"] = kalite
                data["patternNo"] = desen
                data["imageUrl"] = imageUrl

                val ref = db.collection("carpets").document(docId)
                batch.set(ref, data, SetOptions.merge())
                successCount++
            }

            batch.commit()
                .addOnSuccessListener {
                    txtStatus.text = "Yüklendi. Başarılı: $successCount, Hatalı: $failCount"
                }
                .addOnFailureListener { e ->
                    Log.e("CarpetQR", "Batch commit failed", e)
                    txtStatus.text = "Yükleme hatası: ${e.message}"
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
