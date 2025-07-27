package com.wlanscanner.data

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequency: Int,
    val level: Int, // Signal strength in dBm
    val timestamp: Long,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f
) {
    // Convert signal level to quality percentage
    fun getSignalQuality(): Int {
        return when {
            level >= -30 -> 100
            level >= -40 -> 90
            level >= -50 -> 80
            level >= -60 -> 70
            level >= -70 -> 60
            level >= -80 -> 50
            level >= -90 -> 30
            else -> 10
        }
    }
    
    // Get security type from capabilities
    fun getSecurityType(): String {
        return when {
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("OWE") -> "OWE"
            capabilities.contains("SAE") -> "WPA3-SAE"
            else -> "Open"
        }
    }
    
    // Get frequency band
    fun getFrequencyBand(): String {
        return when {
            frequency < 3000 -> "2.4 GHz"
            frequency < 6000 -> "5 GHz"
            else -> "6 GHz"
        }
    }
    
    // Get formatted location string
    fun getLocationString(): String {
        return "Lat: %.6f, Lon: %.6f".format(latitude, longitude)
    }
}
