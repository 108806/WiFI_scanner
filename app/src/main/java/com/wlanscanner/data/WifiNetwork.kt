package com.wlanscanner.data

import com.wlanscanner.utils.ChannelMapper

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
    val accuracy: Float = 0.0f,
    val address: String = "", // Reverse geocoded address
    val vendor: String = "Unknown", // Vendor from MAC OUI lookup
    val anomalies: MutableList<String> = mutableListOf()
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
    
    // Get frequency band with global support
    fun getFrequencyBand(): String {
        val channelInfo = ChannelMapper.getChannelInfo(frequency)
        return channelInfo.band
    }
    
    // Get WiFi channel from frequency using global mapping
    fun getChannel(): Int {
        val channelInfo = ChannelMapper.getChannelInfo(frequency)
        return channelInfo.channel
    }
    
    // Get channel info with region details
    fun getChannelInfo(): ChannelMapper.ChannelInfo {
        return ChannelMapper.getChannelInfo(frequency)
    }
    
    // Generate unique key for hash map: hex(ssid) + "_" + bssid
    fun generateKey(): String {
        val ssidHex = if (ssid.isEmpty()) "hidden" else ssid.toByteArray().joinToString("") { "%02x".format(it) }
        return "${ssidHex}_${bssid.lowercase()}"
    }
    
    // Calculate distance between two locations in meters
    fun distanceFrom(latitude: Double, longitude: Double): Double {
        if (this.latitude == 0.0 && this.longitude == 0.0) return 0.0
        if (latitude == 0.0 && longitude == 0.0) return 0.0
        
        val earthRadius = 6371000.0 // meters
        val lat1Rad = Math.toRadians(this.latitude)
        val lat2Rad = Math.toRadians(latitude)
        val deltaLatRad = Math.toRadians(latitude - this.latitude)
        val deltaLngRad = Math.toRadians(longitude - this.longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    // Get formatted location string
    fun getLocationString(): String {
        return "Lat: %.6f, Lon: %.6f".format(latitude, longitude)
    }
    
    // Get formatted address string (if available)
    fun getFormattedAddress(): String {
        return if (address.isNotEmpty()) {
            address
        } else if (latitude != 0.0 && longitude != 0.0) {
            "GPS: ${getLocationString()}"
        } else {
            "Location unknown"
        }
    }
}
