package com.wlanscanner

import android.content.Context
import android.util.Log

/**
 * Vendor lookup based on MAC address OUI (Organizationally Unique Identifier)
 * First 3 bytes of MAC address identify the manufacturer
 */
class VendorLookup(private val context: Context? = null) {
    
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
        println("VendorLookup: === CONSTRUCTOR CALLED ===")
        println("VendorLookup: Initializing with context: $context")
        Log.d("VendorLookup", "=== CONSTRUCTOR CALLED ===")
        Log.d("VendorLookup", "Initializing VendorLookup with context: $context")
        loadOuiDatabase()
        Log.d("VendorLookup", "=== CONSTRUCTOR FINISHED ===")
        println("VendorLookup: === CONSTRUCTOR FINISHED ===")
    }

    private fun loadOuiDatabase() {
        Log.d("VendorLookup", "=== loadOuiDatabase() called ===")
        // Only load once per session
        if (ouiVendorMap.isNotEmpty()) {
            Log.d("VendorLookup", "OUI database already loaded, skipping. Size: ${ouiVendorMap.size}")
            return
        }
        
        if (context == null) {
            println("VendorLookup: No context provided, cannot load OUI database")
            Log.e("VendorLookup", "No context provided, cannot load OUI database")
            return
        }
        
        Log.d("VendorLookup", "Loading OUI database from assets...")
        try {
            Log.d("VendorLookup", "Opening assets file: oui.txt")
            val inputStream = context.assets.open("oui.txt")
            Log.d("VendorLookup", "Successfully opened oui.txt file")
            
            var lineCount = 0
            var matchCount = 0
            inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    lineCount++
                    if (lineCount <= 10) {
                        Log.d("VendorLookup", "Processing line $lineCount: $line")
                    }
                    // Try multiple patterns for OUI lines
                    // Pattern 1: "28-6F-B9   (hex)		Nokia Shanghai Bell Co., Ltd."
                    val regex1 = Regex("([0-9A-F]{2}-[0-9A-F]{2}-[0-9A-F]{2})\\s+\\(hex\\)\\s+(.+)")
                    // Pattern 2: "286FB9     (base 16)		Nokia Shanghai Bell Co., Ltd."
                    val regex2 = Regex("([0-9A-F]{6})\\s+\\(base 16\\)\\s+(.+)")
                    
                    var match = regex1.find(line)
                    var oui: String? = null
                    var vendorName: String? = null
                    
                    if (match != null) {
                        oui = match.groupValues[1].replace("-", ":").uppercase()
                        vendorName = match.groupValues[2].trim()
                    } else {
                        match = regex2.find(line)
                        if (match != null) {
                            val rawOui = match.groupValues[1]
                            oui = "${rawOui.substring(0,2)}:${rawOui.substring(2,4)}:${rawOui.substring(4,6)}"
                            vendorName = match.groupValues[2].trim()
                        }
                    }
                    
                    if (oui != null && vendorName != null) {
                        matchCount++
                        if (matchCount <= 5) {
                            Log.d("VendorLookup", "Match $matchCount: OUI=$oui, Vendor=$vendorName")
                        }
                        ouiVendorMap[oui] = VendorInfo(
                            name = vendorName,
                            fullName = vendorName,
                            country = "",
                            isCommon = false,
                            securityRisk = SecurityRisk.LOW
                        )
                    }
                }
            }
            Log.d("VendorLookup", "Processed $lineCount lines total, found $matchCount OUI entries")
            Log.d("VendorLookup", "Final OUI database size: ${ouiVendorMap.size}")
            println("VendorLookup: Loaded ${ouiVendorMap.size} OUI entries from assets")
            Log.d("VendorLookup", "Loaded ${ouiVendorMap.size} OUI entries from assets")
        } catch (e: Exception) {
            println("VendorLookup: Failed to load OUI database from assets: ${e.message}")
            Log.e("VendorLookup", "Failed to load OUI database from assets: ${e.message}", e)
        }
    }
    
    fun lookupVendor(macAddress: String): VendorInfo {
        // Force reload if database is empty
        if (ouiVendorMap.isEmpty()) {
            Log.d("VendorLookup", "OUI database is empty, forcing reload...")
            loadOuiDatabase()
        }
        
        val cleanMac = macAddress.replace(":", "").replace("-", "").uppercase()
        if (cleanMac.length < 6) {
            Log.d("VendorLookup", "Invalid MAC address: $macAddress")
            return VendorInfo("Invalid", "Invalid MAC Address", "", false, SecurityRisk.HIGH)
        }
        val oui = "${cleanMac.substring(0, 2)}:${cleanMac.substring(2, 4)}:${cleanMac.substring(4, 6)}"
        Log.d("VendorLookup", "Looking up OUI: $oui for MAC: $macAddress, OUI database size: ${ouiVendorMap.size}")
        
        // Debug: Print first few OUI entries if database is small
        if (ouiVendorMap.size < 5) {
            Log.d("VendorLookup", "OUI database seems empty or very small. Size: ${ouiVendorMap.size}")
            ouiVendorMap.entries.take(5).forEach { (key, value) ->
                Log.d("VendorLookup", "Sample OUI: $key -> ${value.name}")
            }
        }
        
        // Try OUI database first
        val ouiVendor = ouiVendorMap[oui]
        if (ouiVendor != null) {
            Log.d("VendorLookup", "Found in OUI database: ${ouiVendor.name}")
            return ouiVendor
        }
        
        // Fallback to local risky vendor database
        val localVendor = localVendorDatabase[oui]
        if (localVendor != null) {
            Log.d("VendorLookup", "Found in local database: ${localVendor.name}")
            return localVendor
        }
        
        // Unknown vendor
        Log.d("VendorLookup", "Unknown vendor for OUI: $oui, OUI database size: ${ouiVendorMap.size}")
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
