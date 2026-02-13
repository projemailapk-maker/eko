package com.carpetqr.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val inputCarpetId = findViewById<EditText>(R.id.inputCarpetId)
        val btnFetch = findViewById<Button>(R.id.btnFetch)
        val btnAdmin = findViewById<Button>(R.id.btnAdmin)
        val imgCarpet = findViewById<ImageView>(R.id.imgCarpet)
        val txtInfo = findViewById<TextView>(R.id.txtInfo)

        btnFetch.isEnabled = false

        val db = FirebaseFirestore.getInstance()

        fun fetchCarpetById(id: String) {
            txtInfo.text = "Yükleniyor..."
            imgCarpet.setImageDrawable(null)

            db.collection("carpets")
                .document(id)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        txtInfo.text = "Bu ID ile halı bulunamadı: $id"
                        return@addOnSuccessListener
                    }

                    val code = doc.getString("code").orEmpty()
                    val name = doc.getString("name").orEmpty()
                    val model = doc.getString("model").orEmpty()
                    val patternNo = doc.getString("patternNo").orEmpty()
                    val imageUrl = doc.getString("imageUrl").orEmpty()

                    txtInfo.text = "Kod: $code\nİsim: $name\nModel: $model\nDesen: $patternNo"

                    if (imageUrl.isNotBlank()) {
                        imgCarpet.load(imageUrl)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CarpetQR", "Firestore get FAILED", e)
                    txtInfo.text = "Hata: ${e.message}"
                }
        }

        btnFetch.setOnClickListener {
            val id = inputCarpetId.text?.toString()?.trim().orEmpty()
            if (id.isBlank()) {
                Toast.makeText(this, "Carpet ID gir", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchCarpetById(id)
        }

        btnAdmin.setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                val uid = it.user?.uid ?: ""
                Log.d("CarpetQR", "Anonymous sign-in OK uid=$uid")
                Toast.makeText(this, "Giriş başarılı", Toast.LENGTH_SHORT).show()
                btnFetch.isEnabled = true
            }
            .addOnFailureListener { e ->
                Log.e("CarpetQR", "Anonymous sign-in FAILED", e)
                Toast.makeText(this, "Giriş başarısız: ${e.message}", Toast.LENGTH_LONG).show()
                btnFetch.isEnabled = false
            }
    }
}