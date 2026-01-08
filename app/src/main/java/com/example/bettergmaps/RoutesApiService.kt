package com.example.bettergmaps

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// --- Request Models ---
data class RoutesRequest(
    val origin: RouteLocation,
    val destination: RouteLocation,
    val travelMode: String, // DRIVE, WALK, BICYCLE, TWO_WHEELER
    val routingPreference: String? = null, // TRAFFIC_AWARE, TRAFFIC_AWARE_OPTIMAL
    val extraComputations: List<String> = listOf("TOLLS"),
    val routeModifiers: RouteModifiers? = null
)

data class RouteLocation(
    val location: LocationData
)

data class LocationData(
    val latLng: LatLngData
)

data class LatLngData(
    val latitude: Double,
    val longitude: Double
)

data class RouteModifiers(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false
)

// --- Response Models ---
data class RoutesResponse(
    val routes: List<Route>?
)

data class Route(
    val distanceMeters: Int,
    val duration: String, // "3600s" format
    val travelAdvisory: TravelAdvisory?,
    val description: String? = null,
    val routeLabels: List<String>? = null
)

data class TravelAdvisory(
    val tollInfo: TollInfo?
)

data class TollInfo(
    val estimatedPrice: List<Money>?
)

data class Money(
    val currencyCode: String, // "TRY"
    val units: String, // "215"
    val nanos: Int // 500000000 = 0.50
)

// --- Service Interface ---
interface RoutesApiService {
    @POST("v2:computeRoutes")
    fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String, // routes.duration,routes.distanceMeters,routes.travelAdvisory.tollInfo
        @Body request: RoutesRequest
    ): Call<RoutesResponse>
}
