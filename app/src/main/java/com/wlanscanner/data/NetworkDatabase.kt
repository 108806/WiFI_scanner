package com.wlanscanner.data

import android.content.Context
import android.location.Geocoder
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
        var anomalies: MutableList<String> = mutableListOf(),
        var address: String? = "" // Geocoded address for display - mutable, nullable for backward compatibility
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
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    init {
        loadDatabase()
    }
    
    // Get address from GPS coordinates using reverse geocoding
    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        try {
            if (latitude == 0.0 || longitude == 0.0) return ""
            
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressParts = mutableListOf<String>()
                
                // Build address string: Country, City, Street, Number
                address.countryCode?.let { addressParts.add(it) }
                address.locality?.let { addressParts.add(it) } // City
                address.subLocality?.let { addressParts.add(it) } // District/Area
                address.thoroughfare?.let { addressParts.add(it) } // Street name
                address.subThoroughfare?.let { addressParts.add(it) } // Street number
                
                val fullAddress = addressParts.joinToString(", ")
                Log.d("NetworkDatabase", "Geocoded address: $fullAddress for coords: $latitude, $longitude")
                return fullAddress
            }
        } catch (e: Exception) {
            Log.e("NetworkDatabase", "Geocoding failed for $latitude, $longitude", e)
        }
        return ""
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
                
                // Rebuild networks list from entries - only use LATEST scan for each network
                networks.clear()
                networkEntries.values.forEach { entry ->
                    // Only create ONE WifiNetwork per entry using the LATEST signal reading
                    val latestSignal = entry.signalHistory.maxByOrNull { it.timestamp }
                    if (latestSignal != null) {
                        // Reconstruct capabilities from security types
                        val capabilities = when {
                            "WPA3" in entry.securityTypes -> "[WPA3-PSK-CCMP][RSN-PSK-CCMP][ESS]"
                            "WPA2" in entry.securityTypes -> "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"
                            "WPA" in entry.securityTypes -> "[WPA-PSK-CCMP][ESS]"
                            "WEP" in entry.securityTypes -> "[WEP][ESS]"
                            else -> "[ESS]" // Open network
                        }
                        
                        val network = WifiNetwork(
                            ssid = entry.ssid,
                            bssid = entry.bssid,
                            capabilities = capabilities,
                            frequency = latestSignal.frequency,
                            level = latestSignal.level,
                            timestamp = latestSignal.timestamp,
                            latitude = entry.locations.lastOrNull()?.latitude ?: 0.0,
                            longitude = entry.locations.lastOrNull()?.longitude ?: 0.0,
                            address = if (entry.address.isNullOrEmpty()) {
                                // Fallback: geocode if address not stored and we have coordinates
                                if (entry.locations.isNotEmpty()) {
                                    val location = entry.locations.last()
                                    getAddressFromLocation(location.latitude, location.longitude)
                                } else ""
                            } else entry.address ?: ""
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
        // Generate proper key: hex(ssid) + "_" + bssid
        var key = network.generateKey()
        
        // Check for existing entry
        var existingEntry = networkEntries[key]
        
        // Handle existing networks - update unless it's a real anomaly
        if (existingEntry != null) {
            val timeDiff = Math.abs(network.timestamp - existingEntry.lastSeen)
            val distance = if (existingEntry.locations.isNotEmpty()) {
                val lastLocation = existingEntry.locations.last()
                network.distanceFrom(lastLocation.latitude, lastLocation.longitude)
            } else 0.0
            
            // Check if this might be an evil twin (same network ID but detected simultaneously in very different locations)
            val isLikelyEvilTwin = distance > 5000 && timeDiff < 60000 // >5km apart but within 1 minute
            
            // Always UPDATE existing entry unless it's a suspected evil twin
            if (!isLikelyEvilTwin) {
                // Normal case - update the existing network entry
                existingEntry.lastSeen = network.timestamp
                existingEntry.scanCount++
                existingEntry.signalHistory.add(SignalReading(network.timestamp, network.level, network.frequency))
                existingEntry.securityTypes.add(network.getSecurityType())
                
                // Merge anomalies
                network.anomalies.forEach { anomaly ->
                    if (anomaly !in existingEntry.anomalies) {
                        existingEntry.anomalies.add(anomaly)
                    }
                }
                
                if (network.latitude != 0.0 && network.longitude != 0.0) {
                    existingEntry.locations.add(LocationReading(network.timestamp, network.latitude, network.longitude, network.accuracy))
                    
                    // Update address if we don't have one yet
                    if (existingEntry.address.isNullOrEmpty()) {
                        existingEntry.address = getAddressFromLocation(network.latitude, network.longitude)
                    }
                }
                
                Log.d("NetworkDatabase", "Updated existing network: $key (scanCount: ${existingEntry.scanCount}, distance: ${distance.toInt()}m, timeDiff: ${timeDiff}ms)")
                saveDatabase()
                return // Exit early - network updated, NOT added to list
            } else {
                // Suspected evil twin - create duplicate entry
                var duplicateCounter = 1
                var newKey = "${network.generateKey()}_evil-twin-${duplicateCounter}"
                while (networkEntries[newKey] != null) {
                    duplicateCounter++
                    newKey = "${network.generateKey()}_evil-twin-${duplicateCounter}"
                }
                key = newKey
                network.anomalies.add("suspected_evil_twin")
                Log.d("NetworkDatabase", "Creating suspected evil twin entry: $key (distance: ${distance.toInt()}m, timeDiff: ${timeDiff}ms)")
            }
        }
        
        // Only add to networks list if this is a NEW entry (first time or legitimate duplicate)
        // For new networks, get address from GPS coordinates
        val networkWithAddress = if (network.address.isNullOrEmpty() && network.latitude != 0.0 && network.longitude != 0.0) {
            val address = getAddressFromLocation(network.latitude, network.longitude)
            network.copy(address = address)
        } else {
            network
        }
        
        networks.add(networkWithAddress)
        
        // Create new network entry (either first time or legitimate duplicate)
        val address = if (networkWithAddress.latitude != 0.0 && networkWithAddress.longitude != 0.0) {
            if (networkWithAddress.address.isNullOrEmpty()) { 
                getAddressFromLocation(networkWithAddress.latitude, networkWithAddress.longitude) 
            } else networkWithAddress.address
        } else ""
        
        val newEntry = NetworkEntry(
            bssid = network.bssid,
            ssid = network.ssid,
            firstSeen = network.timestamp,
            lastSeen = network.timestamp,
            scanCount = 1,
            signalHistory = mutableListOf(SignalReading(network.timestamp, network.level, network.frequency)),
            locations = mutableListOf(),
            securityTypes = mutableSetOf(network.getSecurityType()),
            anomalies = network.anomalies.toMutableList(),
            address = address
        )
        
        if (network.latitude != 0.0 && network.longitude != 0.0) {
            newEntry.locations.add(LocationReading(network.timestamp, network.latitude, network.longitude, network.accuracy))
        }
        
        networkEntries[key] = newEntry
        Log.d("NetworkDatabase", "Created new network entry with key: $key")
        
        Log.d("NetworkDatabase", "Added network: ${network.ssid} (${network.bssid}) with key: $key")
        saveDatabase() // Save after each addition
    }
    
    // For compatibility with MainActivity - includes anomaly detection
    fun addOrUpdateNetwork(network: WifiNetwork): List<String> {
        val detectedAnomalies = mutableListOf<String>()
        
        // Check for evil twin (same SSID, different BSSID) - but exclude dual-band routers
        val existingSSIDNetworks = networkEntries.values.filter { 
            it.ssid == network.ssid && it.bssid != network.bssid && network.ssid.isNotEmpty()
        }
        if (existingSSIDNetworks.isNotEmpty()) {
            // Check if this might be a dual-band router (same SSID on different frequency bands)
            val networkBand = when {
                network.frequency < 3000 -> "2.4GHz"
                network.frequency < 6000 -> "5GHz"
                else -> "6GHz"
            }
            
            // Check if ALL existing networks with same SSID are in same location but different bands
            val allSameLocationDifferentBands = existingSSIDNetworks.all { existingEntry ->
                val existingBand = existingEntry.signalHistory.lastOrNull()?.frequency?.let { freq ->
                    when {
                        freq < 3000 -> "2.4GHz"
                        freq < 6000 -> "5GHz"
                        else -> "6GHz"
                    }
                } ?: "unknown"
                
                // Calculate distance between networks
                val distance = if (existingEntry.locations.isNotEmpty() && network.latitude != 0.0) {
                    val lastLocation = existingEntry.locations.last()
                    network.distanceFrom(lastLocation.latitude, lastLocation.longitude)
                } else 0.0
                
                // Same location (<200m) and different frequency bands = likely dual-band router
                val sameLocation = distance < 200
                val differentBands = networkBand != existingBand && existingBand != "unknown"
                
                Log.d("NetworkDatabase", "SSID: ${network.ssid}, Current: $networkBand, Existing: $existingBand, Distance: ${distance.toInt()}m, Same location: $sameLocation, Different bands: $differentBands")
                
                sameLocation && differentBands
            }
            
            // Only flag as evil twin if NOT all networks are dual-band router setup
            if (!allSameLocationDifferentBands) {
                detectedAnomalies.add("evil_twin")
                Log.d("NetworkDatabase", "Evil twin detected for ${network.ssid}: not a dual-band setup")
            } else {
                Log.d("NetworkDatabase", "Dual-band router detected for ${network.ssid}: $networkBand band")
            }
        }
        
        // Check for super strong signal (unusually strong for typical WiFi)
        if (network.level > -20) {
            detectedAnomalies.add("super_strong_signal")
        }
        
        // Check for weak encryption
        val securityType = network.getSecurityType()
        if (securityType == "WEP" || securityType == "Open") {
            detectedAnomalies.add("weak_encryption")
        }
        
        // Check for unusual frequency
        if (network.frequency < 2400 || network.frequency > 6000) {
            detectedAnomalies.add("unusual_frequency")
        }
        
        // Check for beacon stuffing (same BSSID with multiple SSIDs)
        val existingBSSIDNetworks = networkEntries.values.filter { 
            it.bssid == network.bssid && it.ssid != network.ssid
        }
        if (existingBSSIDNetworks.isNotEmpty()) {
            detectedAnomalies.add("beacon_stuffing")
        }
        
        // Add detected anomalies to network
        network.anomalies.addAll(detectedAnomalies)
        
        // Add network to database
        addNetwork(network)
        
        return detectedAnomalies
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
    
    fun removeDuplicatesFromDatabase() {
        Log.d("NetworkDatabase", "Starting duplicate removal. Current entries: ${networkEntries.size}")
        
        // Rebuild networks list without duplicates
        networks.clear()
        networkEntries.values.forEach { entry ->
            val latestSignal = entry.signalHistory.maxByOrNull { it.timestamp }
            if (latestSignal != null) {
                val capabilities = when {
                    "WPA3" in entry.securityTypes -> "[WPA3-PSK-CCMP][RSN-PSK-CCMP][ESS]"
                    "WPA2" in entry.securityTypes -> "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"
                    "WPA" in entry.securityTypes -> "[WPA-PSK-CCMP][ESS]"
                    "WEP" in entry.securityTypes -> "[WEP][ESS]"
                    else -> "[ESS]"
                }
                
                val network = WifiNetwork(
                    ssid = entry.ssid,
                    bssid = entry.bssid,
                    capabilities = capabilities,
                    frequency = latestSignal.frequency,
                    level = latestSignal.level,
                    timestamp = latestSignal.timestamp,
                    latitude = entry.locations.lastOrNull()?.latitude ?: 0.0,
                    longitude = entry.locations.lastOrNull()?.longitude ?: 0.0,
                    address = if (entry.address.isNullOrEmpty()) {
                        // Geocode if no address stored and we have coordinates
                        if (entry.locations.isNotEmpty()) {
                            val location = entry.locations.last()
                            getAddressFromLocation(location.latitude, location.longitude)
                        } else ""
                    } else entry.address ?: ""
                )
                networks.add(network)
            }
        }
        
        Log.d("NetworkDatabase", "Duplicate removal complete. Unique entries: ${networkEntries.size}, Networks: ${networks.size}")
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
                "networks" to networkEntries,  // Export as hashmap with keys
                "rawNetworkList" to networks,   // Keep raw list for debugging
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
