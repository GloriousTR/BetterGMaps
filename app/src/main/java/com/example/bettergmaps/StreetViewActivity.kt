package com.example.bettergmaps

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.LatLng

class StreetViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_street_view)

        val lat = intent.getDoubleExtra("LAT", 0.0)
        val lng = intent.getDoubleExtra("LNG", 0.0)
        val target = LatLng(lat, lng)

        val streetViewFragment = supportFragmentManager
            .findFragmentById(R.id.street_view_panorama) as SupportStreetViewPanoramaFragment?

        streetViewFragment?.getStreetViewPanoramaAsync { panorama ->
            panorama.setPosition(target)
        }
    }
}
