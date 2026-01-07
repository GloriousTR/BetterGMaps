package com.example.bettergmaps

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface SpeedLimitService {
    // Overpass API Query
    // Example: data=[out:json];way[maxspeed](around:25,lat,lon);out tags;
    @GET("api/interpreter")
    fun getSpeedLimit(@Query("data") query: String): Call<OverpassResponse>
}

data class OverpassResponse(
    val elements: List<Element>?
)

data class Element(
    val tags: Tags?
)

data class Tags(
    val maxspeed: String?
)
