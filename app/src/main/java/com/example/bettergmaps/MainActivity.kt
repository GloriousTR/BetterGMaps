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
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.android.libraries.places.api.net.PlacesClient

// Navigation SDK Imports
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.SupportNavigationFragment
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.Waypoint
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity() {

    private var mNavigator: Navigator? = null
    private var mMap: GoogleMap? = null
    
    private lateinit var speedLimitService: SpeedLimitService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var placesClient: PlacesClient

    // UI Elements
    private lateinit var textSpeedValue: TextView
    private lateinit var textLimitValue: TextView
    private lateinit var cardHazardAlert: CardView
    private lateinit var textHazardMessage: TextView
    private lateinit var btnLayers: View
    private lateinit var textSearchBar: TextView

    private var isFirstLocation = true
    private var currentSpeedKmH = 0
    private var lastApiCallTime = 0L

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var isAlerting = false

    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                place.latLng?.let { latLng ->
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    mMap?.addMarker(MarkerOptions().position(latLng).title(place.name))
                    textSearchBar.text = place.name
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Theme Logic
        val themePref = getSharedPreferences("BetterGMapsPrefs", MODE_PRIVATE).getInt("theme_pref", 0)
        when (themePref) {
            0 -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            2 -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        }
        
        setContentView(R.layout.activity_main)
        
        // Initialize Navigation SDK
        initializeNavigationSdk()

        // Initialize Places API
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            if (!Places.isInitialized() && apiKey != null) {
                Places.initialize(applicationContext, apiKey)
            }
            placesClient = Places.createClient(this)
        } catch (e: Exception) { e.printStackTrace() }

        // Initialize Retrofit (Speed Limits)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        speedLimitService = retrofit.create(SpeedLimitService::class.java)

        // Bind UI
        textSpeedValue = findViewById(R.id.text_speed_value)
        textLimitValue = findViewById(R.id.text_limit_value)
        cardHazardAlert = findViewById(R.id.card_hazard_alert)
        textHazardMessage = findViewById(R.id.text_hazard_message)
        btnLayers = findViewById(R.id.btn_layers)
        textSearchBar = findViewById(R.id.search_bar_text)

        btnLayers.setOnClickListener { showLayersBottomSheet() }
        textSearchBar.setOnClickListener { startSearch() }
        findViewById<View>(R.id.search_bar_container)?.setOnClickListener { startSearch() }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        // Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        
        // Chips
        findViewById<View>(R.id.btn_chip_home).setOnClickListener {
             getPlace("key_home")?.let { navigateTo(it) } ?: Toast.makeText(this, "Ev kayıtlı değil.", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.nav_explore).setOnClickListener {
             if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc -> loc?.let { updateCamera(it, 15f) } }
             }
        }
        findViewById<View>(R.id.nav_saved).setOnClickListener { showSavedPlacesSheet() }
    }

    private fun initializeNavigationSdk() {
        NavigationApi.getNavigator(this, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(navigator: Navigator) {
                mNavigator = navigator
                
                // Get the Map instance from the fragment
                val navFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportNavigationFragment
                navFragment.getMapAsync { googleMap ->
                    mMap = googleMap
                    setupMapInteractions()
                }
            }
            override fun onError(type: Int) {
                Toast.makeText(this@MainActivity, "Navigation SDK Hatası: $type", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setupMapInteractions() {
        val map = mMap ?: return
        
        map.isTrafficEnabled = true
        map.uiSettings.isCompassEnabled = true
        
        // Click Listeners
        map.setOnMapClickListener { latLng -> showDeepPressSheet(latLng) }
        map.setOnMapLongClickListener { latLng -> showDeepPressSheet(latLng) }
        map.setOnPoiClickListener { poi -> showPoiDetailsSheet(poi) }
        
        enableLocation()
    }

    // --- In-App Navigation Logic ---
    private fun startInAppNavigation(lat: Double, lng: Double) {
        val navigator = mNavigator
        if (navigator != null) {
            val waypoint = Waypoint.builder()
                .setLatLng(lat, lng)
                .build()
            
            navigator.setDestination(waypoint)
            navigator.startGuidance()
            
            Toast.makeText(this, "Navigasyon Başlatıldı", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Navigasyon Servisi Hazır Değil", Toast.LENGTH_SHORT).show()
        }
    }

    // --- UI Sheets ---
    private fun showPoiDetailsSheet(poi: com.google.android.gms.maps.model.PointOfInterest) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_poi, null)
        dialog.setContentView(view)
        
        view.findViewById<TextView>(R.id.poi_name).text = poi.name
        
        // Directions -> Start Navigation
        view.findViewById<View>(R.id.btn_directions).setOnClickListener {
            dialog.dismiss()
            startInAppNavigation(poi.latLng.latitude, poi.latLng.longitude)
        }

        // Street View Logic
        val imgPreview = view.findViewById<android.widget.ImageView>(R.id.img_street_view_preview)
        val cardPreview = view.findViewById<View>(R.id.card_street_view_preview)
        
        Thread {
            try {
                 val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                 val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
                 val imageUrl = "https://maps.googleapis.com/maps/api/streetview?size=600x300&location=${poi.latLng.latitude},${poi.latLng.longitude}&fov=90&heading=0&pitch=0&key=$apiKey"
                 val url = java.net.URL(imageUrl)
                 val bmp = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                 runOnUiThread { imgPreview.setImageBitmap(bmp) }
            } catch(e: Exception) {}
        }.start()
        
        cardPreview.setOnClickListener {
             val intent = android.content.Intent(this, StreetViewActivity::class.java)
             intent.putExtra("LAT", poi.latLng.latitude)
             intent.putExtra("LNG", poi.latLng.longitude)
             startActivity(intent)
             dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_close_poi).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeepPressSheet(latLng: LatLng) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_poi, null)
        dialog.setContentView(view)

        // UI References
        val textName = view.findViewById<TextView>(R.id.poi_name)
        val textAddress = view.findViewById<TextView>(R.id.poi_address)
        val imgPreview = view.findViewById<android.widget.ImageView>(R.id.img_street_view_preview)
        val cardPreview = view.findViewById<View>(R.id.card_street_view_preview)

        textName.text = "Seçilen Konum"
        textAddress.text = "${String.format("%.5f", latLng.latitude)}, ${String.format("%.5f", latLng.longitude)}"
        view.findViewById<TextView>(R.id.poi_rating).visibility = View.GONE
        view.findViewById<TextView>(R.id.poi_open_status).visibility = View.GONE

        // 1. Reverse Geocoding (Async)
        Thread {
            try {
                val geocoder = android.location.Geocoder(this, java.util.Locale("tr", "TR"))
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val featureName = address.featureName
                    val thoroughfare = address.thoroughfare
                    
                    runOnUiThread {
                        if (thoroughfare != null) {
                            textName.text = "$thoroughfare No:$featureName"
                        } else {
                            textName.text = featureName ?: "İsimsiz Konum"
                        }
                        textAddress.text = address.getAddressLine(0)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()

        // 2. Load Street View Image
        Thread {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
                val imageUrl = "https://maps.googleapis.com/maps/api/streetview?size=600x300&location=${latLng.latitude},${latLng.longitude}&fov=90&heading=0&pitch=0&key=$apiKey"
                val url = java.net.URL(imageUrl)
                val bmp = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                runOnUiThread { imgPreview.setImageBitmap(bmp) }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
        
        // Listeners
        view.findViewById<View>(R.id.btn_directions).setOnClickListener {
            dialog.dismiss()
            startInAppNavigation(latLng.latitude, latLng.longitude)
        }

        view.findViewById<View>(R.id.btn_save_place).setOnClickListener {
            dialog.dismiss()
            savePlace("key_home", SavedLoc("Kaydedilen Konum", latLng.latitude, latLng.longitude))
            Toast.makeText(this, "Konum kaydedildi", Toast.LENGTH_SHORT).show()
        }

        cardPreview.setOnClickListener {
             val intent = android.content.Intent(this, StreetViewActivity::class.java)
             intent.putExtra("LAT", latLng.latitude)
             intent.putExtra("LNG", latLng.longitude)
             startActivity(intent)
             dialog.dismiss()
        }

        view.findViewById<View>(R.id.btn_close_poi).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- Helpers ---
    private fun startSearch() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).setCountry("TR").build(this)
        startAutocomplete.launch(intent)
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap?.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                for (loc in res.locations) {
                    updateSpeed(loc)
                    // We only move camera if NOT navigating? For now, let's keep it simple.
                    // If Navigator is active, it handles camera usually.
                    // Implementation note: When Guidance is active, the SDK controls the camera.
                }
            }
        }
    }

    private fun updateSpeed(loc: Location) {
        val speedKmh = (loc.speed * 3.6).toInt()
        textSpeedValue.text = speedKmh.toString()
        val limit = textLimitValue.text.toString().toIntOrNull() ?: 50
        textLimitValue.setTextColor(if (speedKmh > limit) 0xFFFF0000.toInt() else 0xFF000000.toInt())
    }

    private fun updateCamera(loc: Location, zoom: Float? = null) {
        val map = mMap ?: return
        if (isFirstLocation || zoom != null) {
            isFirstLocation = false
            val update = CameraUpdateFactory.newCameraPosition(
                com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(zoom ?: 18f).tilt(45f).bearing(loc.bearing).build()
            )
            map.animateCamera(update)
        }
    }

    data class SavedLoc(val name: String, val lat: Double, val lng: Double)
    private fun savePlace(key: String, loc: SavedLoc) {
        getSharedPreferences("BetterGMapsPrefs", MODE_PRIVATE).edit().putString(key, com.google.gson.Gson().toJson(loc)).apply()
    }
    private fun getPlace(key: String): SavedLoc? {
        val json = getSharedPreferences("BetterGMapsPrefs", MODE_PRIVATE).getString(key, null) ?: return null
        return com.google.gson.Gson().fromJson(json, SavedLoc::class.java)
    }
    private fun navigateTo(loc: SavedLoc) {
        mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), 18f))
    }

    private fun showLayersBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_layers, null)
        view.findViewById<View>(R.id.layer_default).setOnClickListener { mMap?.mapType = GoogleMap.MAP_TYPE_NORMAL; dialog.dismiss() }
        view.findViewById<View>(R.id.layer_satellite).setOnClickListener { mMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE; dialog.dismiss() }
        view.findViewById<View>(R.id.layer_traffic).setOnClickListener { 
            mMap?.let { it.isTrafficEnabled = !it.isTrafficEnabled }
            dialog.dismiss() 
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showSavedPlacesSheet() {
        val dialog = BottomSheetDialog(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val title = TextView(this).apply { text = "Kaydedilenler"; textSize = 20f; setPadding(0, 0, 0, 20) }
        layout.addView(title)
        getPlace("key_home")?.let { home ->
            layout.addView(android.widget.Button(this).apply { text = "Ev"; setOnClickListener { navigateTo(home); dialog.dismiss() } })
        }
        dialog.setContentView(layout)
        dialog.show()
    }
}
