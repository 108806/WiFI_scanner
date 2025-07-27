package com.wlanscanner.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class NetworkDatabase private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: NetworkDatabase? = null
        
        fun getInstance(context: Context): NetworkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Simple NetworkEntry for displaying in RecyclerView
    data class NetworkEntry(
        val bssid: String,
        val ssid: String,
        val firstSeen: Long,
        var lastSeen: Long,
        var scanCount: Int,
        val signalHistory: MutableList<SignalReading>,
        val locations: MutableList<LocationReading>,
        val securityTypes: MutableSet<String>,
        var anomalies: MutableList<String> = mutableListOf()
    )
    
    data class SignalReading(
        val timestamp: Long,
        val level: Int,
        val frequency: Int
    )
    
    data class LocationReading(
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float
    )
    
    private val networks = mutableListOf<WifiNetwork>()
    private val networkEntries = mutableMapOf<String, NetworkEntry>()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val databaseFile = File(context.filesDir, "wifi_networks.json")
    
    init {
        loadDatabase()
    }
    
    private fun loadDatabase() {
        try {
            if (databaseFile.exists()) {
                Log.d("NetworkDatabase", "Loading database from ${databaseFile.absolutePath}")
                val jsonString = databaseFile.readText()
                val type = object : TypeToken<Map<String, NetworkEntry>>() {}.type
                val loadedEntries: Map<String, NetworkEntry> = gson.fromJson(jsonString, type) ?: emptyMap()
                
                networkEntries.clear()
                networkEntries.putAll(loadedEntries)
                
                // Rebuild networks list from entries
                networks.clear()
                networkEntries.values.forEach { entry ->
                    entry.signalHistory.forEach { signal ->
                        val network = WifiNetwork(
                            ssid = entry.ssid,
                            bssid = entry.bssid,
                            capabilities = entry.securityTypes.firstOrNull() ?: "",
                            frequency = signal.frequency,
                            level = signal.level,
                            timestamp = signal.timestamp,
                            latitude = entry.locations.lastOrNull()?.latitude ?: 0.0,
                            longitude = entry.locations.lastOrNull()?.longitude ?: 0.0
                        )
                        networks.add(network)
                    }
                }
                
                Log.d("NetworkDatabase", "Loaded ${networkEntries.size} network entries, ${networks.size} total scans")
            } else {
                Log.d("NetworkDatabase", "No existing database found, starting fresh")
            }
        } catch (e: Exception) {
            Log.e("NetworkDatabase", "Failed to load database", e)
            // Continue with empty database
        }
    }
    
    private fun saveDatabase() {
        try {
            val jsonString = gson.toJson(networkEntries)
            databaseFile.writeText(jsonString)
            Log.d("NetworkDatabase", "Database saved to ${databaseFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("NetworkDatabase", "Failed to save database", e)
        }
    }
    
    fun addNetwork(network: WifiNetwork) {
        networks.add(network)
        
        // Create or update NetworkEntry
        val key = "${network.bssid}_${network.ssid}"
        val entry = networkEntries[key]
        
        if (entry == null) {
            // New network
            val newEntry = NetworkEntry(
                bssid = network.bssid,
                ssid = network.ssid,
                firstSeen = network.timestamp,
                lastSeen = network.timestamp,
                scanCount = 1,
                signalHistory = mutableListOf(SignalReading(network.timestamp, network.level, network.frequency)),
                locations = mutableListOf(),
                securityTypes = mutableSetOf(network.getSecurityType())
            )
            
            if (network.latitude != 0.0 && network.longitude != 0.0) {
                newEntry.locations.add(LocationReading(network.timestamp, network.latitude, network.longitude, network.accuracy))
            }
            
            networkEntries[key] = newEntry
        } else {
            // Update existing
            entry.lastSeen = network.timestamp
            entry.scanCount++
            entry.signalHistory.add(SignalReading(network.timestamp, network.level, network.frequency))
            entry.securityTypes.add(network.getSecurityType())
            
            if (network.latitude != 0.0 && network.longitude != 0.0) {
                entry.locations.add(LocationReading(network.timestamp, network.latitude, network.longitude, network.accuracy))
            }
        }
        
        Log.d("NetworkDatabase", "Added network: ${network.ssid} (${network.bssid})")
        saveDatabase() // Save after each addition
    }
    
    // For compatibility with MainActivity
    fun addOrUpdateNetwork(network: WifiNetwork): List<String> {
        addNetwork(network)
        return emptyList() // No anomaly detection in simple version
    }
    
    fun getAllNetworks(): List<WifiNetwork> {
        return networks.toList()
    }
    
    fun getAllNetworkEntries(): List<NetworkEntry> {
        return networkEntries.values.sortedByDescending { it.lastSeen }
    }
    
    fun getNetworkStats(): Map<String, Any> {
        val uniqueSSIDs = networks.map { it.ssid }.toSet().size
        val locationMismatches = 0 // Placeholder
        val duplicates = 0 // Placeholder
        
        return mapOf(
            "totalNetworks" to networks.size,
            "uniqueSSIDs" to uniqueSSIDs,
            "locationMismatches" to locationMismatches,
            "duplicates" to duplicates
        )
    }
    
    fun clearAllNetworks() {
        networks.clear()
        networkEntries.clear()
        saveDatabase()
        Log.d("NetworkDatabase", "Database cleared")
    }
    
    fun exportToDownloads(): String {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "wifiscanner_export_${timestamp}.json"
            
            // Use Downloads directory
            val downloadsDir = File("/storage/emulated/0/Download")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, fileName)
            
            val exportData = mapOf(
                "metadata" to mapOf(
                    "exportTime" to System.currentTimeMillis(),
                    "version" to "1.0",
                    "totalNetworks" to networks.size,
                    "uniqueNetworks" to networkEntries.size
                ),
                "statistics" to getNetworkStats(),
                "networks" to networks,
                "networkEntries" to networkEntries.values
            )
            
            val jsonString = gson.toJson(exportData)
            
            FileWriter(file).use { writer ->
                writer.write(jsonString)
            }
            
            Log.d("NetworkDatabase", "Exported ${networks.size} networks to Downloads/$fileName")
            return fileName
        } catch (e: Exception) {
            Log.e("NetworkDatabase", "Export to Downloads failed", e)
            throw e
        }
    }
}
