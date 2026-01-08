package com.example.bettergmaps

// Force Git Update
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val name: String,
    val date: String,
    val lat: Double,
    val lng: Double
)

object HistoryManager {
    private const val PREF_NAME = "BetterGMapsHistory"
    private const val KEY_HISTORY = "history_list"

    fun addPlace(context: Context, name: String, lat: Double, lng: Double) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY_HISTORY, null)
        val type = object : TypeToken<ArrayList<HistoryItem>>() {}.type
        val list: ArrayList<HistoryItem> = if (json != null) {
            gson.fromJson(json, type)
        } else {
            ArrayList()
        }

        val timestamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        list.add(0, HistoryItem(name, timestamp, lat, lng)) // Add to top

        // Limit to last 10
        if (list.size > 10) {
            list.removeAt(list.size - 1)
        }

        prefs.edit().putString(KEY_HISTORY, gson.toJson(list)).apply()
    }

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY_HISTORY, null)
        val type = object : TypeToken<ArrayList<HistoryItem>>() {}.type
        return if (json != null) {
            gson.fromJson(json, type)
        } else {
            ArrayList()
        }
    }
    
    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
