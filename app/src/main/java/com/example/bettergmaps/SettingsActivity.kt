package com.example.bettergmaps

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.widget.Button
import android.widget.Toast
import android.view.View

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var textCurrentTheme: TextView
    private lateinit var textCurrentUnit: TextView
    private lateinit var switchVoice: Switch
    private lateinit var switchAvoidTolls: Switch
    private lateinit var switchAvoidHighways: Switch
    
    // Auth Views
    private lateinit var textAccountName: TextView
    private lateinit var textAccountEmail: TextView
    private lateinit var btnSignIn: android.widget.Button
    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("BetterGMapsPrefs", MODE_PRIVATE)

        // Init Views
        textCurrentTheme = findViewById(R.id.text_current_theme)
        textCurrentUnit = findViewById(R.id.text_current_unit)
        switchVoice = findViewById(R.id.switch_voice)
        switchAvoidTolls = findViewById(R.id.switch_avoid_tolls)
        switchAvoidHighways = findViewById(R.id.switch_avoid_highways)
        
        textAccountName = findViewById(R.id.text_account_name)
        textAccountEmail = findViewById(R.id.text_account_email)
        btnSignIn = findViewById(R.id.btn_sign_in)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // --- Auth Init ---
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        
        // Check existing login
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this)
        updateAuthUI(account)
        
        btnSignIn.setOnClickListener {
            if (btnSignIn.text == "Giriş") {
                startActivityForResult(googleSignInClient.signInIntent, 9001)
            } else {
                googleSignInClient.signOut().addOnCompleteListener { 
                    updateAuthUI(null)
                }
            }
        }

        // --- Theme Setting ---
        updateThemeText(prefs.getInt("theme_pref", 0))
        findViewById<LinearLayout>(R.id.setting_theme).setOnClickListener {
            showThemeSelectionDialog()
        }

        // --- Units Setting ---
        updateUnitText(prefs.getString("unit_pref", "auto"))
        findViewById<LinearLayout>(R.id.setting_units).setOnClickListener {
            showUnitSelectionDialog()
        }

        // --- Voice Setting ---
        switchVoice.isChecked = prefs.getBoolean("voice_pref", true)
        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("voice_pref", isChecked).apply()
        }
        
        // --- Route Settings ---
        switchAvoidTolls.isChecked = prefs.getBoolean("avoid_tolls", false)
        switchAvoidTolls.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("avoid_tolls", isChecked).apply()
        }
        
        switchAvoidHighways.isChecked = prefs.getBoolean("avoid_highways", false)
        switchAvoidHighways.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("avoid_highways", isChecked).apply()
        }
        
        // --- Location History ---
        findViewById<android.view.View>(R.id.btn_location_history).setOnClickListener {
             val uri = android.net.Uri.parse("https://timeline.google.com")
             val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
             startActivity(intent)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9001) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                updateAuthUI(account)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                android.widget.Toast.makeText(this, "Giriş Başarısız: ${e.statusCode}", android.widget.Toast.LENGTH_SHORT).show()
                updateAuthUI(null)
            }
        }
    }
    
    private fun updateAuthUI(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        if (account != null) {
            textAccountName.text = account.displayName
            textAccountEmail.text = account.email
            btnSignIn.text = "Çıkış"
        } else {
            textAccountName.text = "Giriş Yapılmadı"
            textAccountEmail.text = "Google ile bağlan"
            btnSignIn.text = "Giriş"
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf("Sistem Varsayılanı (Otomatik)", "Açık Tema (Gündüz)", "Koyu Tema (Gece)")
        val currentThemeIndex = prefs.getInt("theme_pref", 0)
        
        AlertDialog.Builder(this)
            .setTitle("Tema Seçin")
            .setSingleChoiceItems(themes, currentThemeIndex) { dialog, which ->
                prefs.edit().putInt("theme_pref", which).apply()
                updateThemeText(which)
                applyTheme(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showUnitSelectionDialog() {
        val units = arrayOf("Otomatik", "Kilometre (km)", "Mil (mi)")
        val currentUnit = prefs.getString("unit_pref", "auto")
        val currentIndex = when(currentUnit) {
            "km" -> 1
            "mi" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Mesafe Birimleri")
            .setSingleChoiceItems(units, currentIndex) { dialog, which ->
                val newValue = when(which) {
                    1 -> "km"
                    2 -> "mi"
                    else -> "auto"
                }
                prefs.edit().putString("unit_pref", newValue).apply()
                updateUnitText(newValue)
                dialog.dismiss()
            }
            .show()
    }

    private fun updateThemeText(theme: Int) {
        textCurrentTheme.text = when (theme) {
            1 -> "Açık Tema"
            2 -> "Koyu Tema"
            else -> "Sistem Varsayılanı"
        }
    }
    
    private fun updateUnitText(unit: String?) {
        textCurrentUnit.text = when (unit) {
            "km" -> "Kilometre"
            "mi" -> "Mil"
            else -> "Otomatik"
        }
    }

    private fun applyTheme(themeIndex: Int) {
        when (themeIndex) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}
