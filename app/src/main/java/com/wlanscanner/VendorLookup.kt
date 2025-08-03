package com.wlanscanner

/**
 * Vendor lookup based on MAC address OUI (Organizationally Unique Identifier)
 * First 3 bytes of MAC address identify the manufacturer
 */
class VendorLookup {
    
    data class VendorInfo(
        val name: String,
        val fullName: String,
        val country: String = "",
        val isCommon: Boolean = false,
        val securityRisk: SecurityRisk = SecurityRisk.LOW
    )
    
    enum class SecurityRisk {
        LOW,     // Znane, bezpieczne urządzenia
        MEDIUM,  // Urządzenia wymagające uwagi
        HIGH,    // Potencjalnie niebezpieczne
        UNKNOWN  // Nieznany vendor
    }
    
    // Dynamic vendor database loaded from oui.txt
    private val ouiVendorMap: HashMap<String, VendorInfo> = HashMap()
    // Local fallback for risky vendors
    private val localVendorDatabase = mapOf(
        "AC:9E:17" to VendorInfo("ASUS", "ASUSTeK Computer", "TW", true, SecurityRisk.LOW),
        "DC:A6:32" to VendorInfo("RaspberryPi", "Raspberry Pi Foundation", "UK", false, SecurityRisk.MEDIUM),
        "00:0F:00" to VendorInfo("Realtek", "Realtek Semiconductor", "TW", false, SecurityRisk.MEDIUM),
        "00:E0:4C" to VendorInfo("Realtek", "Realtek Semiconductor", "TW", false, SecurityRisk.MEDIUM),
        "00:19:DB" to VendorInfo("Generic", "Unknown Chinese Vendor", "CN", false, SecurityRisk.MEDIUM),
        "00:02:72" to VendorInfo("Generic", "Unknown Vendor", "", false, SecurityRisk.UNKNOWN),
        "00:13:37" to VendorInfo("Hak5", "Hak5 WiFi Pineapple", "US", false, SecurityRisk.HIGH),
        "00:C0:CA" to VendorInfo("Hak5", "Hak5 LLC", "US", false, SecurityRisk.HIGH),
        "B8:27:EB" to VendorInfo("RaspberryPi", "Raspberry Pi Foundation", "UK", false, SecurityRisk.MEDIUM),
        "30:AE:A4" to VendorInfo("ESP32", "Espressif Systems", "CN", false, SecurityRisk.MEDIUM),
        "5C:CF:7F" to VendorInfo("ESP8266", "Espressif Systems", "CN", false, SecurityRisk.MEDIUM),
        "24:0A:C4" to VendorInfo("ESP32", "Espressif Systems", "CN", false, SecurityRisk.MEDIUM),
        "00:00:00" to VendorInfo("NULL", "Invalid MAC Address", "", false, SecurityRisk.HIGH),
        "FF:FF:FF" to VendorInfo("BROADCAST", "Broadcast Address", "", false, SecurityRisk.HIGH)
    )

    init {
        loadOuiDatabase()
    }

    private fun loadOuiDatabase() {
        // Only load once per session
        if (ouiVendorMap.isNotEmpty()) return
        try {
            val file = java.io.File("oui.txt")
            if (!file.exists()) return
            file.forEachLine { line ->
                // Example line: "28-6F-B9   (hex)\t\tNokia Shanghai Bell Co., Ltd."
                val regex = Regex("([0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2})\\s+\\(hex\\)\\s+(.+)")
                val match = regex.find(line)
                if (match != null) {
                    val oui = match.groupValues[1].replace("-", ":").uppercase()
                    val vendorName = match.groupValues[2].trim()
                    ouiVendorMap[oui] = VendorInfo(
                        name = vendorName,
                        fullName = vendorName,
                        country = "",
                        isCommon = false,
                        securityRisk = SecurityRisk.LOW
                    )
                }
            }
        } catch (e: Exception) {
            println("Failed to load OUI database: ${e.message}")
        }
    }
    
    fun lookupVendor(macAddress: String): VendorInfo {
        val cleanMac = macAddress.replace(":", "").replace("-", "").uppercase()
        if (cleanMac.length < 6) {
            return VendorInfo("Invalid", "Invalid MAC Address", "", false, SecurityRisk.HIGH)
        }
        val oui = "${cleanMac.substring(0, 2)}:${cleanMac.substring(2, 4)}:${cleanMac.substring(4, 6)}"
        // Try OUI database first
        val ouiVendor = ouiVendorMap[oui]
        if (ouiVendor != null) return ouiVendor
        // Fallback to local risky vendor database
        val localVendor = localVendorDatabase[oui]
        if (localVendor != null) return localVendor
        // Unknown vendor
        return VendorInfo(
            "Unknown",
            "Unknown Vendor (OUI: $oui)",
            "",
            false,
            SecurityRisk.UNKNOWN
        )
    }
    
    fun isLocallyAdministered(macAddress: String): Boolean {
        val cleanMac = macAddress.replace(":", "").replace("-", "")
        if (cleanMac.length < 2) return false
        
        val firstByte = cleanMac.substring(0, 2).toIntOrNull(16) ?: return false
        return (firstByte and 0x02) != 0  // Second bit indicates locally administered
    }
    
    fun isMulticast(macAddress: String): Boolean {
        val cleanMac = macAddress.replace(":", "").replace("-", "")
        if (cleanMac.length < 2) return false
        
        val firstByte = cleanMac.substring(0, 2).toIntOrNull(16) ?: return false
        return (firstByte and 0x01) != 0  // First bit indicates multicast
    }
    
    fun analyzeMacSecurity(macAddress: String): MacSecurityAnalysis {
        val vendor = lookupVendor(macAddress)
        val isRandomized = isLocallyAdministered(macAddress)
        val isMulticast = isMulticast(macAddress)
        
        val suspiciousFactors = mutableListOf<String>()
        
        if (isRandomized) suspiciousFactors.add("MAC randomization detected")
        if (isMulticast) suspiciousFactors.add("Multicast address")
        if (vendor.securityRisk == SecurityRisk.HIGH) suspiciousFactors.add("High-risk vendor")
        if (vendor.securityRisk == SecurityRisk.UNKNOWN) suspiciousFactors.add("Unknown vendor")
        if (macAddress.startsWith("00:00:00")) suspiciousFactors.add("Null MAC pattern")
        
        return MacSecurityAnalysis(
            vendor = vendor,
            isRandomized = isRandomized,
            isMulticast = isMulticast,
            suspiciousFactors = suspiciousFactors,
            riskLevel = calculateRiskLevel(vendor, suspiciousFactors)
        )
    }
    
    private fun calculateRiskLevel(vendor: VendorInfo, suspiciousFactors: List<String>): SecurityRisk {
        return when {
            suspiciousFactors.size >= 3 -> SecurityRisk.HIGH
            vendor.securityRisk == SecurityRisk.HIGH -> SecurityRisk.HIGH
            suspiciousFactors.size >= 2 -> SecurityRisk.MEDIUM
            vendor.securityRisk == SecurityRisk.MEDIUM -> SecurityRisk.MEDIUM
            suspiciousFactors.isNotEmpty() -> SecurityRisk.MEDIUM
            else -> SecurityRisk.LOW
        }
    }
    
    data class MacSecurityAnalysis(
        val vendor: VendorInfo,
        val isRandomized: Boolean,
        val isMulticast: Boolean,
        val suspiciousFactors: List<String>,
        val riskLevel: SecurityRisk
    )
}
