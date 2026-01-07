package com.example.bettergmaps

import com.example.bettergmaps.R
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.gms.maps.model.MapStyleOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // UI Elements
    private lateinit var textSpeedValue: TextView
    private lateinit var textLimitValue: TextView
    private lateinit var cardLimitBadge: CardView
    private lateinit var cardHazardAlert: CardView
    private lateinit var textHazardMessage: TextView

    // State
    private var isMapReady = false
    private var currentSpeedKmH = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI References
        textSpeedValue = findViewById(R.id.text_speed_value)
        textLimitValue = findViewById(R.id.text_limit_value)
        cardLimitBadge = findViewById(R.id.card_limit_badge)
        cardHazardAlert = findViewById(R.id.card_hazard_alert)
        textHazardMessage = findViewById(R.id.text_hazard_message)

        findViewById<View>(R.id.fab_report).setOnClickListener {
            Toast.makeText(this, "Reporting Incident...", Toast.LENGTH_SHORT).show()
        }

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        // Initialize Map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateSpeed(location)
                    checkHazards(location)
                    updateCamera(location)
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true

        // Yandex-like Dark Mode (pseudo-code idea, keeping standard for now or TODO)
        // mMap.setMapStyle(...)

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
        // location.speed is in m/s. Convert to km/h
        val speedMs = location.speed
        val speedKmh = (speedMs * 3.6).toInt()
        currentSpeedKmH = speedKmh

        textSpeedValue.text = speedKmh.toString()

        // 1. SPEED LIMIT LOGIC
        // In a real app, we would query OpenStreetMap (Overpass API) here based on Lat/Lng.
        // For this demo, we simulate limits based on speed to show the UI change.
        // If driving fast (>80), assume highway (90 limit). If slow, assume city (50).
        val limit = if (speedKmh > 80) 120 else 50
        
        textLimitValue.text = limit.toString()

        // Visual Warning: If speeding, turn boundary RED and pulse
        if (speedKmh > limit) {
             cardLimitBadge.setCardBackgroundColor(0xFFFF0000.toInt()) // Red Warning
             // Optional: Play beep sound here
        } else {
             cardLimitBadge.setCardBackgroundColor(0xFF00C853.toInt()) // Green Safe
        }
    }

    private fun checkHazards(location: Location) {
        // 2. HAZARD LOGIC (Mock Geofencing)
        // We simulate a hazard if the user coordinates match a "Fake Hazard Zone"
        // or effectively, we randomly trigger it for DEMO purposes every 60 seconds
        // OR simpler: specific coordinate box around Istanbul/Ankara for testing.
        
        // For Demo: If speed is exactly 30 (just to test), show "School Zone"
        if (currentSpeedKmH == 30) {
            showHazardAlert("School Zone", R.drawable.ic_launcher) // TODO: Use real icon
        } else if (currentSpeedKmH == 45) {
            showHazardAlert("Speed Bump Ahead", R.drawable.ic_launcher)
        } else {
            hideHazardAlert()
        }
    }
    
    // Helper to animate alerting
    private fun showHazardAlert(message: String, iconRes: Int) {
        if (cardHazardAlert.visibility != View.VISIBLE) {
            textHazardMessage.text = message
            cardHazardAlert.visibility = View.VISIBLE
            cardHazardAlert.alpha = 0f
            cardHazardAlert.animate().alpha(1f).setDuration(500).start()
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
        val currentLatLng = LatLng(location.latitude, location.longitude)
        
        // Google Maps Navigation Style Camera
        // Tilt: 45 degrees, Zoom: 18 (Close up)
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(
            com.google.android.gms.maps.model.CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(18f)
                .tilt(45f) // 3D Effect
                .bearing(location.bearing) // Rotate with user
                .build()
        )
        mMap.animateCamera(cameraUpdate)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation()
            }
        }
    }
}
