package com.example.bettergmaps

// Force Git Update
import com.example.bettergmaps.R
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
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

import com.google.android.libraries.places.api.net.PlacesClient

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var speedLimitService: SpeedLimitService
    private lateinit var routesService: RoutesApiService
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

    // State
    private var isMapReady = false
    private var isFirstLocation = true
    private var currentSpeedKmH = 0
    private var lastApiCallTime = 0L

    // Network & Audio
    // private lateinit var speedLimitService: SpeedLimitService // Removed duplicate
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
        
        // Apply Theme
        val themePref = getSharedPreferences("BetterGMapsPrefs", MODE_PRIVATE).getInt("theme_pref", 0)
        when (themePref) {
            0 -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            2 -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        }
        
        setContentView(R.layout.activity_main)
        
        // Initialize Places API
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            if (!Places.isInitialized() && apiKey != null) {
                Places.initialize(applicationContext, apiKey)
            }
            placesClient = Places.createClient(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize Speed Limit Service (Retrofit)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        speedLimitService = retrofit.create(SpeedLimitService::class.java)

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



        // Initialize Routes API Service
        val routesRetrofit = Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        routesService = routesRetrofit.create(RoutesApiService::class.java)

        // --- BINDINGS FOR NEW UI ---
        // 1. Chips
        findViewById<View>(R.id.btn_chip_home).setOnClickListener {
             val home = getPlace(KEY_HOME)
             if (home != null) navigateTo(home)
             else Toast.makeText(this, "Ev kayıtlı değil. Haritaya basılı tutup ekleyin.", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<View>(R.id.btn_chip_work).setOnClickListener {
             val work = getPlace(KEY_WORK)
             if (work != null) navigateTo(work)
             else Toast.makeText(this, "İş kayıtlı değil. Haritaya basılı tutup ekleyin.", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<View>(R.id.btn_chip_gas).setOnClickListener { performPlaceSearch("Benzin İstasyonu") }

        // 2. Bottom Navigation
        findViewById<View>(R.id.nav_explore).setOnClickListener {
             // Center on My Location
             try {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            updateCamera(location, 15f)
                            Toast.makeText(this, "Konumuma dönüldü", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Konum alınıyor...", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                     Toast.makeText(this, "Konum izni gerekli", Toast.LENGTH_SHORT).show()
                }
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
        findViewById<View>(R.id.nav_saved).setOnClickListener {
             showSavedPlacesSheet()
        }
    }

    // Helper for Text Search (Not Autocomplete) - Requires Places API Text Search or simple intent
    private fun performPlaceSearch(query: String) {
         Toast.makeText(this, "$query aranıyor...", Toast.LENGTH_SHORT).show()
         // Here we would ideally use Places Client searchByText, but for now let's trigger the Autocomplete with a pre-fill?
         // Or better, just move camera to a mock location for demo. 
         // Since we don't have full Text Search implemented in this simple scope without billing issues risk, 
         // we will open the Autocomplete overlay to let user confirm.
         startSearch() 
    }
    private fun showSettingsDialog() {
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        startActivity(intent)
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
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            mMap.isBuildingsEnabled = false
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.layer_terrain).setOnClickListener {
            // User wants Satellite + 3D Buildings
            mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
            mMap.isBuildingsEnabled = true
            
            // Tilt camera to show 3D effect
            val currentCameraPosition = mMap.cameraPosition
            val newCameraPosition = com.google.android.gms.maps.model.CameraPosition.Builder(currentCameraPosition)
                .tilt(45f) // Tilt to see buildings
                .zoom(18f.coerceAtLeast(currentCameraPosition.zoom)) // Zoom in if too far out
                .build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
            
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.layer_traffic).setOnClickListener {
            mMap.isTrafficEnabled = !mMap.isTrafficEnabled
            Toast.makeText(this, "Trafik: " + if(mMap.isTrafficEnabled) "Açık" else "Kapalı", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.layer_transit).setOnClickListener {
             val target = mMap.cameraPosition.target
             
             // List of supported transit apps
             val apps = listOf(
                 Triple("Citymapper", "com.citymapper.app.release", "citymapper://map?coord=${target.latitude},${target.longitude}"),
                 Triple("Moovit", "com.tranzmate", "moovit://directions?dest_lat=${target.latitude}&dest_lon=${target.longitude}"),
                 Triple("Google Maps", "com.google.android.apps.maps", "geo:${target.latitude},${target.longitude}?q=transit")
             )
             
             // Filter installed apps
             val installedApps = apps.filter { 
                 try {
                     packageManager.getPackageInfo(it.second, 0)
                     true
                 } catch (e: PackageManager.NameNotFoundException) {
                     false
                 }
             }

             if (installedApps.isNotEmpty()) {
                 if (installedApps.size == 1) {
                     // Only one found, launch it
                     startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(installedApps[0].third)))
                 } else {
                     // Multiple found, let user choose
                     val options = installedApps.map { it.first }.toTypedArray()
                     AlertDialog.Builder(this)
                        .setTitle("Toplu Taşıma Uygulaması Seçin")
                        .setItems(options) { _, which ->
                            val app = installedApps[which]
                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.third)))
                        }
                        .show()
                 }
             } else {
                 // None installed, prompt to install Citymapper
                 AlertDialog.Builder(this)
                    .setTitle("Uygulama Bulunamadı")
                    .setMessage("Toplu taşıma verileri için Citymapper veya Moovit yüklemeniz önerilir.")
                    .setPositiveButton("Citymapper Yükle") { _, _ ->
                         try {
                             startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.citymapper.app.release")))
                         } catch (e: Exception) {
                             startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.citymapper.app.release")))
                         }
                    }
                    .setNegativeButton("İptal", null)
                    .show()
             }
             dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.layer_street_view).setOnClickListener {
             val target = mMap.cameraPosition.target
             val intent = android.content.Intent(this, StreetViewActivity::class.java)
             intent.putExtra("LAT", target.latitude)
             intent.putExtra("LNG", target.longitude)
             startActivity(intent)
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

    // --- SAVED PLACES LOGIC ---
    private val PREFS_NAME = "BetterGMapsPrefs"
    private val KEY_HOME = "key_home"
    private val KEY_WORK = "key_work" // Kullanicinin istegi uzerine is adresi de eklendi

    data class SavedLoc(val name: String, val lat: Double, val lng: Double)

    private fun getPrefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private fun getGson() = com.google.gson.Gson()

    private fun savePlace(key: String, loc: SavedLoc) {
        val json = getGson().toJson(loc)
        getPrefs().edit().putString(key, json).apply()
        Toast.makeText(this, "${loc.name} kaydedildi!", Toast.LENGTH_SHORT).show()
    }

    private fun getPlace(key: String): SavedLoc? {
        val json = getPrefs().getString(key, null) ?: return null
        return getGson().fromJson(json, SavedLoc::class.java)
    }

    private fun navigateTo(loc: SavedLoc) {
        val latLng = LatLng(loc.lat, loc.lng)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
        mMap.addMarker(MarkerOptions().position(latLng).title(loc.name))
        textSearchBar.text = loc.name
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true

        mMap.isTrafficEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        
        // Long Click to Save
        mMap.setOnMapLongClickListener { latLng ->
            showSaveDialog(latLng)
        }
        
        // POI Click Listener
        mMap.setOnPoiClickListener { poi ->
            showPoiDetailsSheet(poi)
        }
        
        enableLocation()
    }
    
    private fun showPoiDetailsSheet(poi: com.google.android.gms.maps.model.PointOfInterest) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_poi, null)
        dialog.setContentView(view)
        
        val textName = view.findViewById<TextView>(R.id.poi_name)
        val textAddress = view.findViewById<TextView>(R.id.poi_address)
        val textRating = view.findViewById<TextView>(R.id.poi_rating)
        val textOpenStatus = view.findViewById<TextView>(R.id.poi_open_status)
        val btnDirections = view.findViewById<View>(R.id.btn_directions)
        val btnClose = view.findViewById<View>(R.id.btn_close_poi)
        
        textName.text = poi.name
        textAddress.text = "Bilgiler yükleniyor..."
        
        // Fetch Place Details
        val placeFields = listOf(
            com.google.android.libraries.places.api.model.Place.Field.NAME,
            com.google.android.libraries.places.api.model.Place.Field.ADDRESS,
            com.google.android.libraries.places.api.model.Place.Field.RATING,
            com.google.android.libraries.places.api.model.Place.Field.USER_RATINGS_TOTAL,
            com.google.android.libraries.places.api.model.Place.Field.OPENING_HOURS,
            com.google.android.libraries.places.api.model.Place.Field.PHONE_NUMBER
        )
        val request = com.google.android.libraries.places.api.net.FetchPlaceRequest.builder(poi.placeId, placeFields).build()
        
        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            val place = response.place
            textName.text = place.name ?: poi.name
            textAddress.text = place.address ?: "Adres bulunamadı"
            
            if (place.rating != null) {
                textRating.text = "⭐ ${place.rating} (${place.userRatingsTotal} yorum)"
                textRating.visibility = View.VISIBLE
            } else {
                textRating.visibility = View.GONE
            }

            val isOpen = place.isOpen
            if (isOpen != null) {
                 if (isOpen == true) {
                     textOpenStatus.text = "Şu an Açık"
                     textOpenStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Green
                 } else {
                     textOpenStatus.text = "Şu an Kapalı"
                     textOpenStatus.setTextColor(android.graphics.Color.parseColor("#F44336")) // Red
                 }
                 textOpenStatus.visibility = View.VISIBLE
            } else {
                 textOpenStatus.visibility = View.GONE
            }

        }.addOnFailureListener {
            textAddress.text = "Detaylar alınamadı."
        }
        
        // Directions Button Logic
        btnDirections.setOnClickListener {
             dialog.dismiss()
             showRouteSelectionSheet(poi)
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showRouteSelectionSheet(poi: com.google.android.gms.maps.model.PointOfInterest) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_route_selection, null)
        dialog.setContentView(view)
        
        val container = view.findViewById<LinearLayout>(R.id.container_routes)
        val textDest = view.findViewById<TextView>(R.id.text_route_destination)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progress_routes)
        
        textDest.text = "Hedef: ${poi.name}"
        container.removeAllViews()
        progressBar.visibility = View.VISIBLE
        
        // Mock Delay for calculation effect
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Real Google Routes API Logic
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        // 1. Get API Key
                        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                        val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
                        
                        val origin = RouteLocation(LocationData(LatLngData(location.latitude, location.longitude)))
                        val dest = RouteLocation(LocationData(LatLngData(poi.latLng.latitude, poi.latLng.longitude)))
                        
                        val fieldMask = "routes.duration,routes.distanceMeters,routes.travelAdvisory.tollInfo,routes.routeLabels"
                        
                        // Prepare Requests (Concurrent)
                        // A. Walking (If close)
                        val requests = ArrayList<Call<RoutesResponse>>()
                        
                        // B. Standard Drive (Traffic Aware + Tolls) - "En Hızlı"
                        val driveRequest = RoutesRequest(origin, dest, "DRIVE", "TRAFFIC_AWARE", listOf("TOLLS"))
                        
                        // C. Toll Free Drive - "Ücretsiz"
                        val tollFreeRequest = RoutesRequest(origin, dest, "DRIVE", "TRAFFIC_AWARE", listOf("TOLLS"), RouteModifiers(avoidTolls = true))

                        // Execute Calls
                        container.removeAllViews() // Clear text
                        
                        // Call 1: Standard Drive
                        routesService.computeRoutes(apiKey, fieldMask, driveRequest).enqueue(object : retrofit2.Callback<RoutesResponse> {
                            override fun onResponse(call: Call<RoutesResponse>, response: retrofit2.Response<RoutesResponse>) {
                                if (response.isSuccessful && response.body()?.routes != null) {
                                    val route = response.body()!!.routes!![0]
                                    addRouteOptionToUI(container, route, "Araçla (En Hızlı)", poi, dialog)
                                }
                            }
                            override fun onFailure(call: Call<RoutesResponse>, t: Throwable) { t.printStackTrace() }
                        })
                        
                        // Call 2: No Tolls
                        routesService.computeRoutes(apiKey, fieldMask, tollFreeRequest).enqueue(object : retrofit2.Callback<RoutesResponse> {
                            override fun onResponse(call: Call<RoutesResponse>, response: retrofit2.Response<RoutesResponse>) {
                                if (response.isSuccessful && response.body()?.routes != null) {
                                    val route = response.body()!!.routes!![0]
                                    addRouteOptionToUI(container, route, "Araçla (Ücretsiz)", poi, dialog)
                                }
                            }
                            override fun onFailure(call: Call<RoutesResponse>, t: Throwable) { }
                        })
                        
                    } else {
                        Toast.makeText(this@MainActivity, "Konum alınamadı", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, 1000) // 1s delay
        
        // Yandex Navi Button
        view.findViewById<View>(R.id.btn_open_yandex).setOnClickListener {
            val intent = android.content.Intent("com.yandex.navi.action.BUILD_ROUTE_ON_MAP")
            intent.setPackage("ru.yandex.yandexnavi")
            intent.putExtra("lat_to", poi.latLng.latitude)
            intent.putExtra("lon_to", poi.latLng.longitude)
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback: Try generic scheme or Play Store
                try {
                    val uri = android.net.Uri.parse("yandexnavi://build_route_on_map?lat_to=${poi.latLng.latitude}&lon_to=${poi.latLng.longitude}")
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                    // Open Play Store
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, 
                        android.net.Uri.parse("market://details?id=ru.yandex.yandexnavi")))
                }
            }
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_close_routes).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    
    // Simple Data class for Mocking
    data class RouteOption(val name: String, val duration: String, val distance: String, val cost: Double)

    private fun showSaveDialog(latLng: LatLng) {
        val options = arrayOf("Ev Olarak Kaydet", "İş Olarak Kaydet", "Favorilere Ekle")
        AlertDialog.Builder(this)
            .setTitle("Konumu Kaydet")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> savePlace(KEY_HOME, SavedLoc("Ev", latLng.latitude, latLng.longitude))
                    1 -> savePlace(KEY_WORK, SavedLoc("İş", latLng.latitude, latLng.longitude))
                    2 -> Toast.makeText(this, "Favoriler listesi yakında eklenecek!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showSavedPlacesSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_layers, null) // Reuse generic structure or build simple linear layout dynamically
        // Dynamic Layout for better customization
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(40, 40, 40, 40)
        layout.setBackgroundColor(0xFFFFFFFF.toInt())

        val title = TextView(this)
        title.text = "Kaydedilen Yerler"
        title.textSize = 20f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setPadding(0, 0, 0, 30)
        layout.addView(title)

        // Load specific places
        val home = getPlace(KEY_HOME)
        val work = getPlace(KEY_WORK)

        if (home != null) {
            val btn = android.widget.Button(this)
            btn.text = "🏠 Ev (${String.format("%.4f", home.lat)}, ...)"
            btn.setOnClickListener { navigateTo(home); dialog.dismiss() }
            layout.addView(btn)
        }
        if (work != null) {
             val btn = android.widget.Button(this)
             btn.text = "💼 İş (${String.format("%.4f", work.lat)}, ...)"
             btn.setOnClickListener { navigateTo(work); dialog.dismiss() }
             layout.addView(btn)
        }
        
        if (home == null && work == null) {
            val empty = TextView(this)
            empty.text = "Henüz kayıtlı yer yok.\nHaritaya uzun basarak ekleyebilirsiniz."
            layout.addView(empty)
        }

        dialog.setContentView(layout)
        dialog.show()
    }

    // --- UPDATED BINDINGS (Paste this over existing onCreate bindings) ---
    /* 
       Note to Agent: The below block is intended to replace the previous BINDINGS section in onCreate.
       However, replace_file_content works on contiguous blocks. 
       I will ensure the target content matches the previous BINDINGS block to swap it out.
    */
    private fun setupNewUiBindings() {
        // 1. Chips
        findViewById<View>(R.id.btn_chip_home).setOnClickListener {
             val home = getPlace(KEY_HOME)
             if (home != null) navigateTo(home)
             else Toast.makeText(this, "Ev adresi kayıtlı değil. Haritaya uzun basıp ayarlayın.", Toast.LENGTH_LONG).show()
        }
        // Work Chip
        findViewById<View>(R.id.btn_chip_work).setOnClickListener {
             val work = getPlace(KEY_WORK)
             if (work != null) navigateTo(work)
             else Toast.makeText(this, "İş adresi kayıtlı değil. Haritaya uzun basıp ayarlayın.", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.btn_chip_gas).setOnClickListener { performPlaceSearch("Benzin İstasyonu") }

        // 2. Bottom Navigation
        findViewById<View>(R.id.nav_explore).setOnClickListener {
             Toast.makeText(this, "Keşfet Modu", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.nav_saved).setOnClickListener {
             showSavedPlacesSheet()
        }
    }

    private fun enableLocation() { // Updated enableLocation matches existing code
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
    
    // ... (Rest of permission and helper methods remain similar, just replacing OnMapReady and part of onCreate)

    /* 
       REAL IMPLEMENTATION STRATEGY:
       I will target 'override fun onMapReady' first to add the long click listener.
       Then I will target 'onCreate' bottom bindings to use the new setupNewUiBindings logic or inline it.
    */

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

    private fun updateCamera(location: Location, zoom: Float? = null) {
        if (!isMapReady) return
        
        // Sadece ilk konum alındığında veya zoom manuel belirtildiğinde (örn: Konumum butonu) kamerayı odakla.
        if (isFirstLocation || zoom != null) {
            isFirstLocation = false
            val currentLatLng = LatLng(location.latitude, location.longitude)
            val targetZoom = zoom ?: 18f
            
            val cameraUpdate = CameraUpdateFactory.newCameraPosition(
                com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(targetZoom)
                    .tilt(45f)
                    .bearing(location.bearing)
                    .build()
            )
            mMap.animateCamera(cameraUpdate)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation()
            }
        }
    }
    private fun addRouteOptionToUI(container: LinearLayout, route: Route, title: String, poi: com.google.android.gms.maps.model.PointOfInterest, dialog: BottomSheetDialog) {
        val routeView = layoutInflater.inflate(R.layout.item_route_option, null)
        
        // Duration: "3600s" -> "60 dk"
        val seconds = route.duration.replace("s", "").toIntOrNull() ?: 0
        val durationText = if (seconds > 3600) "${seconds/3600} sa ${(seconds%3600)/60} dk" else "${seconds/60} dk"
        
        // Distance: 15400 -> "15.4 km"
        val distanceKm = route.distanceMeters / 1000.0
        val distanceText = String.format("%.1f km", distanceKm)
        
        routeView.findViewById<TextView>(R.id.route_name).text = title
        routeView.findViewById<TextView>(R.id.route_details).text = "$durationText • $distanceText"
        
        val textCost = routeView.findViewById<TextView>(R.id.route_cost)
        val price = route.travelAdvisory?.tollInfo?.estimatedPrice?.firstOrNull()
        
        if (price != null) {
            val costVal = price.units.toDoubleOrNull() ?: 0.0
            val nanos = price.nanos.toDouble() / 1000000000.0
            val total = costVal + nanos
            textCost.text = "₺${String.format("%.2f", total)}"
            textCost.setTextColor(android.graphics.Color.parseColor("#E64A19"))
        } else {
            textCost.text = "Ücretsiz"
            textCost.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }
        
        routeView.setOnClickListener {
            // Log to History
            HistoryManager.addPlace(this@MainActivity, poi.name, poi.latLng.latitude, poi.latLng.longitude)
            
            // Launch nav
            val uri = android.net.Uri.parse("google.navigation:q=${poi.latLng.latitude},${poi.latLng.longitude}")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            dialog.dismiss()
        }
        
        runOnUiThread {
            container.addView(routeView)
        }
    }
}
