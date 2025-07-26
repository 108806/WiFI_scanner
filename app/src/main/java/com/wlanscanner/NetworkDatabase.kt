package com.wlanscanner

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest
import java.util.*
import kotlin.collections.HashMap

/**
 * Persistent network database using BSSID_hex(SSID) as keys
 * Stores network history and detects anomalies
 */
class NetworkDatabase(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkDatabase"
        private const val DATABASE_FILE = "network_database.json"
        private const val SIGNAL_VARIANCE_THRESHOLD = 15 // dBm
        private const val EVIL_TWIN_THRESHOLD = 100 // meters
    }
    
    private val gson = Gson()
    private val databaseFile = File(context.filesDir, DATABASE_FILE)
    private var networkHistory: HashMap<String, NetworkEntry> = HashMap()
    
    data class NetworkEntry(
        val bssid: String,
        val ssid: String,
        val firstSeen: Long,
        var lastSeen: Long,
        var scanCount: Int,
        val signalHistory: MutableList<SignalReading>,
        val locations: MutableList<LocationReading>,
        val securityTypes: MutableSet<String>,
        var anomalies: MutableList<AnomalyRecord>
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
    
    data class AnomalyRecord(
        val timestamp: Long,
        val type: AnomalyType,
        val description: String,
        val severity: AnomalySeverity
    )
    
    enum class AnomalyType {
        EVIL_TWIN,
        SIGNAL_ANOMALY,
        SECURITY_DOWNGRADE,
        FREQUENCY_CHANGE,
        LOCATION_MISMATCH,
        HIDDEN_NETWORK,
        WEAK_SECURITY
    }
    
    enum class AnomalySeverity {
        INFO, WARNING, CRITICAL
    }
    
    init {
        loadDatabase()
    }
    
    /**
     * Generate unique key for network: BSSID_hex(SSID)
     */
    private fun generateNetworkKey(bssid: String, ssid: String): String {
        val hexSsid = if (ssid.isNotEmpty()) {
            ssid.toByteArray().joinToString("") { "%02x".format(it) }
        } else {
            "hidden"
        }
        return "${bssid}_$hexSsid"
    }
    
    /**
     * Add or update network in database with anomaly detection
     */
    fun addOrUpdateNetwork(network: WifiNetwork): List<AnomalyRecord> {
        val key = generateNetworkKey(network.bssid, network.ssid)
        val currentTime = System.currentTimeMillis()
        val detectedAnomalies = mutableListOf<AnomalyRecord>()
        
        val entry = networkHistory[key]
        
        if (entry == null) {
            // New network - create entry
            val newEntry = NetworkEntry(
                bssid = network.bssid,
                ssid = network.ssid,
                firstSeen = currentTime,
                lastSeen = currentTime,
                scanCount = 1,
                signalHistory = mutableListOf(SignalReading(currentTime, network.level, network.frequency)),
                locations = mutableListOf(),
                securityTypes = mutableSetOf(network.getSecurityType()),
                anomalies = mutableListOf()
            )
            
            // Add location if available
            if (network.latitude != null && network.longitude != null && network.latitude != 0.0 && network.longitude != 0.0) {
                newEntry.locations.add(LocationReading(currentTime, network.latitude, network.longitude, (network.accuracy ?: 0.0).toFloat()))
            }
            
            // Check for security anomalies on first detection
            detectedAnomalies.addAll(checkSecurityAnomalies(network, newEntry))
            
            newEntry.anomalies.addAll(detectedAnomalies)
            networkHistory[key] = newEntry
            
            Log.d(TAG, "New network added: $key")
        } else {
            // Existing network - update and check for anomalies
            entry.lastSeen = currentTime
            entry.scanCount++
            entry.signalHistory.add(SignalReading(currentTime, network.level, network.frequency))
            entry.securityTypes.add(network.getSecurityType())
            
            // Add location if available
            if (network.latitude != null && network.longitude != null && network.latitude != 0.0 && network.longitude != 0.0) {
                entry.locations.add(LocationReading(currentTime, network.latitude, network.longitude, (network.accuracy ?: 0.0).toFloat()))
            }
            
            // Detect anomalies
            detectedAnomalies.addAll(detectAllAnomalies(network, entry))
            entry.anomalies.addAll(detectedAnomalies)
            
            // Limit history size (keep last 100 readings)
            if (entry.signalHistory.size > 100) {
                entry.signalHistory.removeAt(0)
            }
            if (entry.locations.size > 50) {
                entry.locations.removeAt(0)
            }
            
            Log.d(TAG, "Network updated: $key, anomalies: ${detectedAnomalies.size}")
        }
        
        saveDatabase()
        return detectedAnomalies
    }
    
    /**
     * Detect all types of anomalies for a network
     */
    private fun detectAllAnomalies(network: WifiNetwork, entry: NetworkEntry): List<AnomalyRecord> {
        val anomalies = mutableListOf<AnomalyRecord>()
        val currentTime = System.currentTimeMillis()
        
        // Signal anomalies
        anomalies.addAll(detectSignalAnomalies(network, entry, currentTime))
        
        // Security anomalies
        anomalies.addAll(checkSecurityAnomalies(network, entry, currentTime))
        
        // Location anomalies
        anomalies.addAll(detectLocationAnomalies(network, entry, currentTime))
        
        // Frequency anomalies
        anomalies.addAll(detectFrequencyAnomalies(network, entry, currentTime))
        
        // Evil twin detection
        anomalies.addAll(detectEvilTwins(network, currentTime))
        
        return anomalies
    }
    
    private fun detectSignalAnomalies(network: WifiNetwork, entry: NetworkEntry, timestamp: Long): List<AnomalyRecord> {
        val anomalies = mutableListOf<AnomalyRecord>()
        
        if (entry.signalHistory.size >= 5) {
            val recentSignals = entry.signalHistory.takeLast(5).map { it.level }
            val average = recentSignals.average()
            val variance = recentSignals.map { (it - average) * (it - average) }.average()
            
            if (variance > SIGNAL_VARIANCE_THRESHOLD * SIGNAL_VARIANCE_THRESHOLD) {
                anomalies.add(AnomalyRecord(
                    timestamp,
                    AnomalyType.SIGNAL_ANOMALY,
                    "Unusual signal variance detected (${String.format("%.1f", Math.sqrt(variance))} dBm)",
                    AnomalySeverity.WARNING
                ))
            }
            
            // Unusually strong signal
            if (network.level > -20) {
                anomalies.add(AnomalyRecord(
                    timestamp,
                    AnomalyType.SIGNAL_ANOMALY,
                    "Unusually strong signal (${network.level} dBm)",
                    AnomalySeverity.INFO
                ))
            }
        }
        
        return anomalies
    }
    
    private fun checkSecurityAnomalies(network: WifiNetwork, entry: NetworkEntry, timestamp: Long = System.currentTimeMillis()): List<AnomalyRecord> {
        val anomalies = mutableListOf<AnomalyRecord>()
        val currentSecurity = network.getSecurityType()
        
        // Security downgrade detection
        if (entry.securityTypes.contains("WPA3") && currentSecurity in listOf("WPA2", "WPA", "WEP", "Open")) {
            anomalies.add(AnomalyRecord(
                timestamp,
                AnomalyType.SECURITY_DOWNGRADE,
                "Security downgrade detected: WPA3 → $currentSecurity",
                AnomalySeverity.CRITICAL
            ))
        } else if (entry.securityTypes.contains("WPA2") && currentSecurity in listOf("WPA", "WEP", "Open")) {
            anomalies.add(AnomalyRecord(
                timestamp,
                AnomalyType.SECURITY_DOWNGRADE,
                "Security downgrade detected: WPA2 → $currentSecurity",
                AnomalySeverity.CRITICAL
            ))
        }
        
        // Weak security warnings
        when (currentSecurity) {
            "Open" -> anomalies.add(AnomalyRecord(
                timestamp,
                AnomalyType.WEAK_SECURITY,
                "Open network - no encryption",
                AnomalySeverity.CRITICAL
            ))
            "WEP" -> anomalies.add(AnomalyRecord(
                timestamp,
                AnomalyType.WEAK_SECURITY,
                "WEP encryption is deprecated and insecure",
                AnomalySeverity.WARNING
            ))
            "WPA" -> anomalies.add(AnomalyRecord(
                timestamp,
                AnomalyType.WEAK_SECURITY,
                "WPA encryption is outdated",
                AnomalySeverity.WARNING
            ))
        }
        
        // Hidden network
        if (network.ssid.isEmpty()) {
            anomalies.add(AnomalyRecord(
                timestamp,
                AnomalyType.HIDDEN_NETWORK,
                "Hidden network detected",
                AnomalySeverity.INFO
            ))
        }
        
        return anomalies
    }
    
    private fun detectLocationAnomalies(network: WifiNetwork, entry: NetworkEntry, timestamp: Long): List<AnomalyRecord> {
        val anomalies = mutableListOf<AnomalyRecord>()
        
        if (network.latitude != null && network.longitude != null && network.latitude != 0.0 && network.longitude != 0.0 && entry.locations.isNotEmpty()) {
            val lastLocation = entry.locations.last()
            val distance = calculateDistance(
                network.latitude, network.longitude,
                lastLocation.latitude, lastLocation.longitude
            )
            
            if (distance > 1000) { // More than 1km
                anomalies.add(AnomalyRecord(
                    timestamp,
                    AnomalyType.LOCATION_MISMATCH,
                    "Network moved ${String.format("%.1f", distance)}m from last location",
                    AnomalySeverity.WARNING
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectFrequencyAnomalies(network: WifiNetwork, entry: NetworkEntry, timestamp: Long): List<AnomalyRecord> {
        val anomalies = mutableListOf<AnomalyRecord>()
        
        if (entry.signalHistory.isNotEmpty()) {
            val lastFrequency = entry.signalHistory.last().frequency
            if (Math.abs(network.frequency - lastFrequency) > 100) { // Significant frequency change
                anomalies.add(AnomalyRecord(
                    timestamp,
                    AnomalyType.FREQUENCY_CHANGE,
                    "Frequency change: ${lastFrequency}MHz → ${network.frequency}MHz",
                    AnomalySeverity.INFO
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectEvilTwins(network: WifiNetwork, timestamp: Long): List<AnomalyRecord> {
        val anomalies = mutableListOf<AnomalyRecord>()
        
        // Look for networks with same SSID but different BSSID
        if (network.ssid.isNotEmpty()) {
            networkHistory.values.forEach { entry ->
                if (entry.ssid == network.ssid && entry.bssid != network.bssid) {
                    // Check if they're close in location (potential evil twin)
                    if (network.latitude != null && network.longitude != null && network.latitude != 0.0 && network.longitude != 0.0 && entry.locations.isNotEmpty()) {
                        val lastLocation = entry.locations.last()
                        val distance = calculateDistance(
                            network.latitude, network.longitude,
                            lastLocation.latitude, lastLocation.longitude
                        )
                        
                        if (distance < EVIL_TWIN_THRESHOLD) {
                            anomalies.add(AnomalyRecord(
                                timestamp,
                                AnomalyType.EVIL_TWIN,
                                "Potential Evil Twin: Same SSID '${network.ssid}' with different BSSID",
                                AnomalySeverity.CRITICAL
                            ))
                        }
                    }
                }
            }
        }
        
        return anomalies
    }
    
    /**
     * Calculate distance between two GPS coordinates in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
    
    /**
     * Get network statistics
     */
    fun getNetworkStats(): Map<String, Any> {
        val totalNetworks = networkHistory.size
        val totalAnomalies = networkHistory.values.sumOf { it.anomalies.size }
        val criticalAnomalies = networkHistory.values.sumOf { entry ->
            entry.anomalies.count { it.severity == AnomalySeverity.CRITICAL }
        }
        
        return mapOf(
            "totalNetworks" to totalNetworks,
            "totalAnomalies" to totalAnomalies,
            "criticalAnomalies" to criticalAnomalies,
            "openNetworks" to networkHistory.values.count { entry ->
                entry.securityTypes.contains("Open")
            }
        )
    }
    
    /**
     * Get anomalies for a specific network
     */
    fun getNetworkAnomalies(bssid: String, ssid: String): List<AnomalyRecord> {
        val key = generateNetworkKey(bssid, ssid)
        return networkHistory[key]?.anomalies ?: emptyList()
    }
    
    /**
     * Export database to JSON
     */
    fun exportToJson(): String {
        val exportData = mapOf(
            "metadata" to mapOf(
                "exportTime" to System.currentTimeMillis(),
                "version" to "1.0",
                "totalNetworks" to networkHistory.size
            ),
            "statistics" to getNetworkStats(),
            "networks" to networkHistory
        )
        return gson.toJson(exportData)
    }
    
    /**
     * Load database from file
     */
    private fun loadDatabase() {
        try {
            if (databaseFile.exists()) {
                val json = databaseFile.readText()
                val type = object : TypeToken<HashMap<String, NetworkEntry>>() {}.type
                networkHistory = gson.fromJson(json, type) ?: HashMap()
                Log.d(TAG, "Database loaded: ${networkHistory.size} networks")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load database", e)
            networkHistory = HashMap()
        }
    }
    
    /**
     * Save database to file
     */
    private fun saveDatabase() {
        try {
            databaseFile.writeText(gson.toJson(networkHistory))
            Log.d(TAG, "Database saved: ${networkHistory.size} networks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save database", e)
        }
    }
    
    /**
     * Clear all data
     */
    fun clearDatabase() {
        networkHistory.clear()
        saveDatabase()
        Log.d(TAG, "Database cleared")
    }
}
