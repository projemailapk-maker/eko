package com.carpetqr.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AdminLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        val inputEmail = findViewById<EditText>(R.id.inputEmail)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)
        val btnLogin = findViewById<Button>(R.id.btnAdminLogin)

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
    }
}
