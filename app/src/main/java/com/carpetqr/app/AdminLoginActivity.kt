package com.carpetqr.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        val inputEmail = findViewById<EditText>(R.id.inputEmail)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)
        val btnLogin = findViewById<Button>(R.id.btnAdminLogin)
        val btnRefreshList = findViewById<Button>(R.id.btnRefreshList)

        btnLogin.setOnClickListener {
            val email = inputEmail.text?.toString()?.trim().orEmpty()
            val password = inputPassword.text?.toString().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Email ve parola gir", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Toast.makeText(this, "Admin giriş başarılı", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, CsvImportActivity::class.java))
                }
                .addOnFailureListener { e ->
                    Log.e("CarpetQR", "Admin login failed", e)
                    Toast.makeText(this, "Giriş başarısız: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        btnRefreshList.setOnClickListener {
            refreshCarpetIndex()
        }

        val txtAbout = findViewById<TextView>(R.id.txtAbout)
        txtAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun refreshCarpetIndex() {
        Toast.makeText(this, "Liste güncelleniyor...", Toast.LENGTH_SHORT).show()

        val prefs = getSharedPreferences("carpetqr", MODE_PRIVATE)
        val db = FirebaseFirestore.getInstance()

        db.collection("carpets")
            .get()
            .addOnSuccessListener { snap ->
                prefs.edit()
                    .putLong("carpet_index_last_fetch", System.currentTimeMillis())
                    .apply()

                Toast.makeText(this, "Liste güncellendi (${snap.size()} halı)", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("CarpetQR", "Index refresh failed", e)
                Toast.makeText(this, "Liste güncellenemedi: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showAboutDialog() {
        val version = getString(R.string.app_version)
        val message = """
            CARPET QR
            Versiyon: $version
            
            © 2026 Tüm hakları saklıdır.
            
            Developed by İlyas YEŞİL
            
            Bu uygulama, halı envanteri yönetimi için geliştirilmiştir. QR kod tarama ve arama özellikleri ile halılarınızı kolayca takip edebilirsiniz.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Hakkında")
            .setMessage(message)
            .setPositiveButton("Tamam") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
