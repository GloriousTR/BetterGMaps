package com.example.bettergmaps

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

        // Mock Speed Limit Logic
        val limit = getMockSpeedLimit(location)
        textLimitValue.text = limit.toString()

        if (speedKmh > limit) {
            cardLimitBadge.setCardBackgroundColor(0xFFFF0000.toInt()) // Red
        } else {
            cardLimitBadge.setCardBackgroundColor(0xFF00FF00.toInt()) // Green (or Grey)
        }
    }

    private fun getMockSpeedLimit(location: Location): Int {
        // In real app, query OSM here.
        // For demo: default 50, highway 90.
        return 50
    }

    private fun checkHazards(location: Location) {
        // Mock Hazard: If lat > X show hazard
        // TODO: Real Geofencing logic
    }

    private fun updateCamera(location: Location) {
        if (!isMapReady) return
        val currentLatLng = LatLng(location.latitude, location.longitude)
        // Camera follows user, tilted for 3D navigation view
        // val cameraUpdate = CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f)
        // mMap.animateCamera(cameraUpdate)
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
