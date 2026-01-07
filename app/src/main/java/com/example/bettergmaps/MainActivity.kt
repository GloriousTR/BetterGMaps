package com.example.bettergmaps

import com.example.bettergmaps.R
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // UI Elements
    private lateinit var textSpeedValue: TextView
    private lateinit var textLimitValue: TextView
    private lateinit var cardHazardAlert: CardView
    private lateinit var textHazardMessage: TextView
    private lateinit var btnLayers: FloatingActionButton
    private lateinit var textSearchBar: TextView

    // State
    private var isMapReady = false
    private var currentSpeedKmH = 0
    private var lastApiCallTime = 0L

    // Network & Audio
    private lateinit var speedLimitService: SpeedLimitService
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var isAlerting = false

    // Search Launcher
    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                // Move Camera to Place
                place.latLng?.let { latLng ->
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    mMap.addMarker(MarkerOptions().position(latLng).title(place.name))
                    textSearchBar.text = place.name // Update Search Bar text
                }
            }
        } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
            val intent = result.data
            if (intent != null) {
                val status = Autocomplete.getStatusFromIntent(intent)
                Toast.makeText(this, "Hata: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Places API
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            if (!Places.isInitialized() && apiKey != null) {
                Places.initialize(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize UI References
        textSpeedValue = findViewById(R.id.text_speed_value)
        textLimitValue = findViewById(R.id.text_limit_value)
        cardHazardAlert = findViewById(R.id.card_hazard_alert)
        textHazardMessage = findViewById(R.id.text_hazard_message)
        btnLayers = findViewById(R.id.btn_layers)
        textSearchBar = findViewById(R.id.search_bar_text)

        // Layers Button Action
        btnLayers.setOnClickListener {
            showLayersBottomSheet()
        }

        // Report FAB
        findViewById<View>(R.id.fab_report).setOnClickListener {
            showReportDialog()
        }

        // Search Bar Action
        textSearchBar.setOnClickListener {
            startSearch()
        }
        findViewById<View>(R.id.search_bar_container)?.setOnClickListener {
             startSearch()
        }

        // Settings Button Action
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            showSettingsDialog()
        }

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        // Initialize Map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize Retrofit for OSM
        val retrofit = Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        speedLimitService = retrofit.create(SpeedLimitService::class.java)
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Hız Birimi (km/h)", "Gece Modu (Otomatik)", "Sesli Uyarılar (Açık)")
        val checkedItems = booleanArrayOf(true, true, true) // Mock state

        AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setMultiChoiceItems(options, checkedItems) { dialog, which, isChecked ->
                // Save setting preference here
                Toast.makeText(this, "Ayar güncellendi", Toast.LENGTH_SHORT).show()
                if (which == 2) { // Audio toggle
                     // update audio state
                }
            }
            .setPositiveButton("Tamam", null)
            .show()
    }
    
    private fun startSearch() {
        if (!Places.isInitialized()) {
             Toast.makeText(this, "API Key Hatası: Places başlatılamadı.", Toast.LENGTH_LONG).show()
             return
        }
        
        // Define fields we want to return
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        
        // Start Autocomplete Intent
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountry("TR") // Limit search to Turkey for better results
            .build(this)
        startAutocomplete.launch(intent)
    }

    private fun showLayersBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_layers, null)
        dialog.setContentView(view)

        // Handle Clicks
        view.findViewById<View>(R.id.layer_default).setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.layer_satellite).setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.layer_terrain).setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.layer_traffic).setOnClickListener {
            mMap.isTrafficEnabled = !mMap.isTrafficEnabled
            Toast.makeText(this, "Trafik: " + if(mMap.isTrafficEnabled) "Açık" else "Kapalı", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showReportDialog() {
        val options = arrayOf("Kaza", "Yolda Çalışma", "Radar", "Kapalı Yol")
        AlertDialog.Builder(this)
            .setTitle("Olay Bildir")
            .setItems(options) { dialog, which ->
                Toast.makeText(this, "${options[which]} bildirildi! Teşekkürler.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateSpeed(location)
                    checkHazards(location)
                    // Only update camera if we are not looking at a search result (simple logic for now)
                    // Or keep '3D Follow' mode active. For now, let's keep it active.
                    updateCamera(location)
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true

        mMap.isTrafficEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        
        enableLocation()
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun updateSpeed(location: Location) {
        val speedMs = location.speed
        val speedKmh = (speedMs * 3.6).toInt()
        currentSpeedKmH = speedKmh

        textSpeedValue.text = speedKmh.toString()

        // Real Speed Limit (Overpass)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastApiCallTime > 2000) { 
            lastApiCallTime = currentTime
            fetchRealSpeedLimit(location.latitude, location.longitude)
        }

        // Logic
        val currentLimitText = textLimitValue.text.toString()
        val limit = currentLimitText.toIntOrNull() ?: 50 

        if (speedKmh > limit) {
             textLimitValue.setTextColor(0xFFFF0000.toInt()) // Kirmizi Yazi
             if (!isAlerting) {
                 toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                 isAlerting = true
             }
        } else {
             textLimitValue.setTextColor(0xFF000000.toInt()) // Siyah Yazi
             isAlerting = false
        }
    }

    private fun fetchRealSpeedLimit(lat: Double, lon: Double) {
        val query = "[out:json];way[maxspeed](around:20,$lat,$lon);out tags;"
        speedLimitService.getSpeedLimit(query).enqueue(object : Callback<OverpassResponse> {
            override fun onResponse(call: Call<OverpassResponse>, response: Response<OverpassResponse>) {
                if (response.isSuccessful) {
                    val elements = response.body()?.elements
                    if (!elements.isNullOrEmpty()) {
                        val maxSpeedStr = elements.first().tags?.maxspeed
                        val maxSpeed = maxSpeedStr?.filter { it.isDigit() }?.toIntOrNull()
                        if (maxSpeed != null) {
                            textLimitValue.text = maxSpeed.toString()
                        }
                    }
                }
            }
            override fun onFailure(call: Call<OverpassResponse>, t: Throwable) {}
        })
    }

    private fun checkHazards(location: Location) {
        if (currentSpeedKmH == 30) {
            showHazardAlert("Okul Bölgesi", R.drawable.ic_launcher)
        } else if (currentSpeedKmH == 45) {
            showHazardAlert("Kasis Var", R.drawable.ic_launcher)
        } else {
            hideHazardAlert()
        }
    }
    
    private fun showHazardAlert(message: String, iconRes: Int) {
        if (cardHazardAlert.visibility != View.VISIBLE) {
            textHazardMessage.text = message
            cardHazardAlert.visibility = View.VISIBLE
            cardHazardAlert.alpha = 0f
            cardHazardAlert.animate().alpha(1f).setDuration(500).start()
            toneGenerator.startTone(ToneGenerator.TONE_SUP_PIP, 150)
        }
    }

    private fun hideHazardAlert() {
        if (cardHazardAlert.visibility == View.VISIBLE) {
            cardHazardAlert.animate().alpha(0f).setDuration(500).withEndAction {
                cardHazardAlert.visibility = View.GONE
            }.start()
        }
    }

    private fun updateCamera(location: Location) {
        if (!isMapReady) return
        // Eger kullanici bir yer aradiysa kamerayi otomatik takip modundan cikartabiliriz
        // Şimdilik sürekli takşp etsin (Navigasyon modu varsayımı)
        val currentLatLng = LatLng(location.latitude, location.longitude)
        
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(
            com.google.android.gms.maps.model.CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(18f)
                .tilt(45f)
                .bearing(location.bearing)
                .build()
        )
        mMap.animateCamera(cameraUpdate)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation()
            }
        }
    }
}
