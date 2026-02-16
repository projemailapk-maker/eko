package com.carpetqr.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var currentZoomLevel = 0
    private val zoomMultipliers = floatArrayOf(1.0f, 1.5f, 2.0f)
    private var baseScale = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var posX = 0f
    private var posY = 0f
    private lateinit var imageMatrix: android.graphics.Matrix
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnScan = findViewById<Button>(R.id.btnScan)
        val inputSearch = findViewById<MaterialAutoCompleteTextView>(R.id.inputSearch)
        val imgCarpet = findViewById<ImageView>(R.id.imgCarpet)
        val txtCarpetName = findViewById<TextView>(R.id.txtCarpetName)
        val txtAdminLink = findViewById<TextView>(R.id.txtAdminLink)

        btnScan.isEnabled = false
        inputSearch.isEnabled = false

        setupImageZoom(imgCarpet)

        val db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences("carpetqr", MODE_PRIVATE)
        val cacheTtlMs = 7L * 24L * 60L * 60L * 1000L
        fun isCacheFresh(now: Long): Boolean {
            val last = prefs.getLong("carpet_index_last_fetch", 0L)
            return last > 0L && (now - last) < cacheTtlMs
        }

        data class CarpetIndexItem(val id: String, val displayName: String, val searchKey: String) {
            override fun toString(): String = displayName
        }

        val indexItems = ArrayList<CarpetIndexItem>(1024)
        val allItems = ArrayList<CarpetIndexItem>(1024)
        val adapter = object : android.widget.ArrayAdapter<CarpetIndexItem>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            ArrayList()
        ) {
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val query = constraint?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                        val results = FilterResults()

                        val filtered = if (query.isBlank()) {
                            emptyList()
                        } else {
                            allItems.asSequence()
                                .filter { it.searchKey.contains(query) }
                                .take(20)
                                .toList()
                        }

                        results.values = filtered
                        results.count = filtered.size
                        return results
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        val items = results?.values as? List<CarpetIndexItem> ?: emptyList()
                        addAll(items)
                        notifyDataSetChanged()
                    }
                }
            }
        }
        inputSearch.setAdapter(adapter)
        inputSearch.threshold = 1

        fun maybeShowDropdown() {
            if (!inputSearch.isEnabled) return
            if ((inputSearch.text?.length ?: 0) >= 1) {
                inputSearch.post { inputSearch.showDropDown() }
            }
        }

        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                maybeShowDropdown()
            }
        })

        inputSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) maybeShowDropdown()
        }

        inputSearch.setOnClickListener {
            if (inputSearch.text?.length ?: 0 >= 1) {
                inputSearch.showDropDown()
            }
            inputSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(inputSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        fun sanitizeId(raw: String): String {
            var s = raw.trim()
            s = s.trim('"', '\'', '\uFEFF')
            s = s.replace("\u200B", "").replace("\u200E", "").replace("\u200F", "")
            return s.trim()
        }

        fun fetchIndex(silent: Boolean) {
            if (!silent) {
                Toast.makeText(this, "Liste güncelleniyor...", Toast.LENGTH_SHORT).show()
            }

            db.collection("carpets")
                .get()
                .addOnSuccessListener { snap ->
                    indexItems.clear()
                    for (doc in snap.documents) {
                        val id = doc.id
                        val rawName = doc.getString("CARPET_NAME")
                            ?: doc.getString("name")
                            ?: ""
                        val normalized = rawName.trim()
                        if (normalized.isNotBlank()) {
                            val display = normalized.uppercase(Locale.getDefault())
                            val key = normalized.lowercase(Locale.getDefault())
                            indexItems.add(CarpetIndexItem(id, display, key))
                        }
                    }
                    indexItems.sortBy { it.searchKey }
                    allItems.clear()
                    allItems.addAll(indexItems)
                    adapter.notifyDataSetChanged()
                    prefs.edit().putLong("carpet_index_last_fetch", System.currentTimeMillis()).apply()
                    if (!silent) {
                        Toast.makeText(this, "Liste güncellendi (${indexItems.size})", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CarpetQR", "Index fetch failed", e)
                    if (!silent) {
                        Toast.makeText(this, "Liste güncellenemedi: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        fun fetchCarpetById(id: String) {
            txtCarpetName.text = "Yükleniyor..."
            imgCarpet.setImageDrawable(null)
            imgCarpet.tag = null
            
            currentZoomLevel = 0
            posX = 0f
            posY = 0f

            db.collection("carpets")
                .document(id)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        txtCarpetName.text = "Bulunamadı"
                        imgCarpet.tag = null
                        return@addOnSuccessListener
                    }

                    val name = doc.getString("CARPET_NAME")
                        ?: doc.getString("name")
                        ?: ""
                    val imageUrl = doc.getString("imageUrl").orEmpty()

                    txtCarpetName.text = name.trim().uppercase(Locale.getDefault())

                    if (imageUrl.isNotBlank()) {
                        imgCarpet.tag = imageUrl
                        imgCarpet.load(imageUrl) {
                            listener(
                                onSuccess = { _, _ ->
                                    imgCarpet.post {
                                        resetToBaseScale(imgCarpet)
                                    }
                                }
                            )
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CarpetQR", "Firestore get FAILED", e)
                    txtCarpetName.text = "Hata"
                    imgCarpet.tag = null
                }
        }

        val qrScanLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val id = sanitizeId(result.data?.getStringExtra(QrScanActivity.EXTRA_CARPET_ID).orEmpty())
            if (id.isBlank()) return@registerForActivityResult
            Log.d("CarpetQR", "QR parsed id=$id")
            Toast.makeText(this, "QR ID: $id", Toast.LENGTH_SHORT).show()
            fetchCarpetById(id)
        }

        inputSearch.setOnItemClickListener { parent, _, position, _ ->
            val item = parent.getItemAtPosition(position) as? CarpetIndexItem ?: return@setOnItemClickListener
            fetchCarpetById(item.id)

            inputSearch.setText("")
            inputSearch.dismissDropDown()
            inputSearch.clearFocus()
            
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(inputSearch.windowToken, 0)
        }

        btnScan.setOnClickListener {
            qrScanLauncher.launch(Intent(this, QrScanActivity::class.java))
        }

        txtAdminLink.setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                val uid = it.user?.uid ?: ""
                Log.d("CarpetQR", "Anonymous sign-in OK uid=$uid")
                Toast.makeText(this, "Giriş başarılı", Toast.LENGTH_SHORT).show()
                btnScan.isEnabled = true
                inputSearch.isEnabled = true

                val now = System.currentTimeMillis()
                if (!isCacheFresh(now) || indexItems.isEmpty()) {
                    fetchIndex(silent = true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CarpetQR", "Anonymous sign-in FAILED", e)
                Toast.makeText(this, "Giriş başarısız: ${e.message}", Toast.LENGTH_LONG).show()
                btnScan.isEnabled = false
                inputSearch.isEnabled = false
            }
    }

    private fun setupImageZoom(imageView: ImageView) {
        imageView.scaleType = ImageView.ScaleType.MATRIX
        imageMatrix = android.graphics.Matrix()

        imageView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                val drawable = imageView.drawable ?: return
                val viewWidth = imageView.width.toFloat()
                val viewHeight = imageView.height.toFloat()
                val drawableWidth = drawable.intrinsicWidth.toFloat()
                val drawableHeight = drawable.intrinsicHeight.toFloat()

                if (viewWidth > 0 && viewHeight > 0 && drawableWidth > 0 && drawableHeight > 0) {
                    val scaleX = viewWidth / drawableWidth
                    val scaleY = viewHeight / drawableHeight
                    baseScale = max(scaleX, scaleY)

                    val scaledWidth = drawableWidth * baseScale
                    val scaledHeight = drawableHeight * baseScale
                    val dx = (viewWidth - scaledWidth) / 2f
                    val dy = (viewHeight - scaledHeight) / 2f

                    imageMatrix.setScale(baseScale, baseScale)
                    imageMatrix.postTranslate(dx, dy)
                    imageView.imageMatrix = imageMatrix
                }
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                currentZoomLevel = (currentZoomLevel + 1) % zoomMultipliers.size
                val multiplier = zoomMultipliers[currentZoomLevel]
                
                if (currentZoomLevel == 0) {
                    posX = 0f
                    posY = 0f
                    resetToBaseScale(imageView)
                } else {
                    applyZoom(imageView, baseScale * multiplier)
                }
                
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        imageView.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)

            if (currentZoomLevel > 0) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY

                        posX += dx
                        posY += dy

                        val multiplier = zoomMultipliers[currentZoomLevel]
                        val scale = baseScale * multiplier
                        
                        val drawable = imageView.drawable
                        if (drawable != null) {
                            val scaledWidth = drawable.intrinsicWidth * scale
                            val scaledHeight = drawable.intrinsicHeight * scale
                            val maxTransX = max(0f, (scaledWidth - view.width) / 2f)
                            val maxTransY = max(0f, (scaledHeight - view.height) / 2f)

                            posX = max(-maxTransX, min(maxTransX, posX))
                            posY = max(-maxTransY, min(maxTransY, posY))

                            applyZoom(imageView, scale)
                        }

                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }
            true
        }
    }

    private fun resetToBaseScale(imageView: ImageView) {
        val drawable = imageView.drawable ?: return
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (viewWidth > 0 && viewHeight > 0 && drawableWidth > 0 && drawableHeight > 0) {
            val scaleX = viewWidth / drawableWidth
            val scaleY = viewHeight / drawableHeight
            baseScale = max(scaleX, scaleY)

            val scaledWidth = drawableWidth * baseScale
            val scaledHeight = drawableHeight * baseScale
            val dx = (viewWidth - scaledWidth) / 2f
            val dy = (viewHeight - scaledHeight) / 2f

            imageMatrix.reset()
            imageMatrix.setScale(baseScale, baseScale)
            imageMatrix.postTranslate(dx, dy)
            imageView.imageMatrix = imageMatrix
        }
    }

    private fun applyZoom(imageView: ImageView, scale: Float) {
        val drawable = imageView.drawable ?: return
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (viewWidth > 0 && viewHeight > 0 && drawableWidth > 0 && drawableHeight > 0) {
            val scaledWidth = drawableWidth * scale
            val scaledHeight = drawableHeight * scale
            val dx = (viewWidth - scaledWidth) / 2f + posX
            val dy = (viewHeight - scaledHeight) / 2f + posY

            imageMatrix.reset()
            imageMatrix.setScale(scale, scale)
            imageMatrix.postTranslate(dx, dy)
            imageView.imageMatrix = imageMatrix
        }
    }
}