package com.wlanscanner

import android.util.Log
import com.wlanscanner.data.NetworkDatabase
import kotlin.math.abs

/**
 * Advanced Security Anomaly Detector for WiFi Networks
 * Detects potential attacks and suspicious network behavior
 */
class SecurityAnomalyDetector(
    private val vendorLookup: VendorLookup = VendorLookup()
) {
    
    data class SecurityReport(
        val timestamp: Long = System.currentTimeMillis(),
        val anomalies: List<SecurityAnomaly>,
        val riskLevel: RiskLevel,
        val summary: String
    )
    
    data class SecurityAnomaly(
        val type: AnomalyType,
        val severity: Severity,
        val description: String,
        val affectedNetworks: List<String> = emptyList(),
        val evidence: Map<String, Any> = emptyMap(),
        val recommendedAction: String
    )
    
    enum class AnomalyType {
        EVIL_TWIN,              // Duplicate SSID with different BSSID
        ROGUE_AP,               // Suspicious access point
        DEAUTH_ATTACK,          // Mass network disappearance
        MAC_SPOOFING,           // Suspicious MAC patterns
        KARMA_ATTACK,           // Common SSIDs appearing suddenly
        SIGNAL_JAMMING,         // Signal interference patterns
        VENDOR_ANOMALY,         // Suspicious vendors
        CHANNEL_HOPPING,        // Rapid channel changes
        BEACON_FLOODING,        // Too many networks from one location
        CAPTIVE_PORTAL_ATTACK   // Suspicious open networks
    }
    
    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum class RiskLevel {
        SAFE, CAUTION, WARNING, DANGER
    }
    
    private var previousScanResults: List<NetworkDatabase.NetworkEntry> = emptyList()
    private var scanHistory: MutableList<ScanSnapshot> = mutableListOf()
    
    data class ScanSnapshot(
        val timestamp: Long,
        val networks: List<NetworkDatabase.NetworkEntry>,
        val networkCount: Int,
        val uniqueVendors: Set<String>
    )
    
    fun analyzeNetworks(currentNetworks: List<NetworkDatabase.NetworkEntry>): SecurityReport {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        // Store current scan
        val snapshot = ScanSnapshot(
            timestamp = System.currentTimeMillis(),
            networks = currentNetworks,
            networkCount = currentNetworks.size,
            uniqueVendors = currentNetworks.map { network -> 
                vendorLookup.lookupVendor(network.bssid).name 
            }.toSet()
        )
        scanHistory.add(snapshot)
        
        // Keep only last 20 scans for analysis
        if (scanHistory.size > 20) {
            scanHistory.removeAt(0)
        }
        
        // Run all anomaly detection algorithms
        anomalies.addAll(detectEvilTwins(currentNetworks))
        anomalies.addAll(detectRogueAPs(currentNetworks))
        anomalies.addAll(detectMassDisappearance(currentNetworks))
        anomalies.addAll(detectSuspiciousVendors(currentNetworks))
        anomalies.addAll(detectMassUnknownVendors(currentNetworks)) // New: mass unknown vendor detection
        anomalies.addAll(detectKarmaAttacks(currentNetworks))
        anomalies.addAll(detectSignalAnomalies(currentNetworks))
        anomalies.addAll(detectBeaconFlooding(currentNetworks))
        anomalies.addAll(detectSuspiciousOpenNetworks(currentNetworks))
        anomalies.addAll(detectChannelAnomalies(currentNetworks))
        
        previousScanResults = currentNetworks
        
        val riskLevel = calculateOverallRisk(anomalies)
        val summary = generateSummary(anomalies, currentNetworks.size)
        
        Log.d("SecurityDetector", "Analysis complete: ${anomalies.size} anomalies, risk: $riskLevel")
        
        return SecurityReport(
            anomalies = anomalies,
            riskLevel = riskLevel,
            summary = summary
        )
    }
    
    private fun detectEvilTwins(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        val ssidGroups = networks.groupBy { it.ssid }
        
        ssidGroups.forEach { (ssid: String, networksWithSameSsid: List<NetworkDatabase.NetworkEntry>) ->
            if (networksWithSameSsid.size > 1 && ssid.isNotEmpty()) {
                val bssids = networksWithSameSsid.map { network -> network.bssid }.distinct()
                if (bssids.size > 1) {
                    // Check if vendors are different (potential evil twin)
                    val vendors = bssids.map { bssid -> vendorLookup.lookupVendor(bssid).name }.distinct()
                    
                    if (vendors.size > 1) {
                        anomalies.add(SecurityAnomaly(
                            type = AnomalyType.EVIL_TWIN,
                            severity = Severity.HIGH,
                            description = "Potential Evil Twin attack: Multiple BSSIDs for SSID '$ssid' from different vendors",
                            affectedNetworks = bssids,
                            evidence = mapOf(
                                "ssid" to ssid,
                                "bssids" to bssids,
                                "vendors" to vendors
                            ),
                            recommendedAction = "Verify legitimate access point. Avoid connecting to suspicious networks."
                        ))
                    }
                }
            }
        }
        
        return anomalies
    }
    
    private fun detectRogueAPs(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        networks.forEach { network ->
            val macAnalysis = vendorLookup.analyzeMacSecurity(network.bssid)
            
            if (macAnalysis.riskLevel == VendorLookup.SecurityRisk.HIGH) {
                anomalies.add(SecurityAnomaly(
                    type = AnomalyType.ROGUE_AP,
                    severity = Severity.HIGH,
                    description = "High-risk access point detected: ${network.ssid} (${network.bssid})",
                    affectedNetworks = listOf(network.bssid),
                    evidence = mapOf(
                        "vendor" to macAnalysis.vendor.name,
                        "suspiciousFactors" to macAnalysis.suspiciousFactors,
                        "isRandomized" to macAnalysis.isRandomized
                    ),
                    recommendedAction = "Do not connect. Potential malicious access point."
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectMassDisappearance(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        if (previousScanResults.isNotEmpty()) {
            val previousCount = previousScanResults.size
            val currentCount = networks.size
            val disappearanceRate = if (previousCount > 0) {
                (previousCount - currentCount).toFloat() / previousCount
            } else 0f
            
            // If more than 50% of networks disappeared suddenly
            if (disappearanceRate > 0.5 && previousCount > 5) {
                val missingNetworks = previousScanResults.map { it.bssid } - networks.map { it.bssid }.toSet()
                
                anomalies.add(SecurityAnomaly(
                    type = AnomalyType.DEAUTH_ATTACK,
                    severity = Severity.CRITICAL,
                    description = "Possible jamming/deauth attack: ${(disappearanceRate * 100).toInt()}% of networks disappeared",
                    affectedNetworks = missingNetworks,
                    evidence = mapOf(
                        "previousCount" to previousCount,
                        "currentCount" to currentCount,
                        "disappearanceRate" to disappearanceRate,
                        "missingCount" to missingNetworks.size
                    ),
                    recommendedAction = "Potential WiFi jamming detected. Monitor for continued interference."
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectSuspiciousVendors(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        networks.forEach { network ->
            val vendor = vendorLookup.lookupVendor(network.bssid)
            
            when (vendor.securityRisk) {
                VendorLookup.SecurityRisk.HIGH -> {
                    anomalies.add(SecurityAnomaly(
                        type = AnomalyType.VENDOR_ANOMALY,
                        severity = Severity.HIGH,
                        description = "High-risk vendor detected: ${vendor.name} (${network.ssid})",
                        affectedNetworks = listOf(network.bssid),
                        evidence = mapOf("vendor" to vendor.name, "fullName" to vendor.fullName),
                        recommendedAction = "Avoid connection. Known penetration testing equipment."
                    ))
                }
                VendorLookup.SecurityRisk.UNKNOWN -> {
                    // Only flag UNKNOWN vendors if they have suspicious characteristics
                    if (network.ssid.isNotEmpty() && hasSuspiciousPattern(network)) {
                        anomalies.add(SecurityAnomaly(
                            type = AnomalyType.VENDOR_ANOMALY,
                            severity = Severity.LOW, // Reduced severity
                            description = "Suspicious unknown vendor pattern: ${network.ssid} (${network.bssid})",
                            affectedNetworks = listOf(network.bssid),
                            evidence = mapOf("bssid" to network.bssid, "reason" to "suspicious_pattern"),
                            recommendedAction = "Monitor for unusual behavior. Unknown vendor with suspicious characteristics."
                        ))
                    }
                }
                else -> { /* Low/Medium risk - no action needed */ }
            }
        }
        
        return anomalies
    }
    
    /**
     * Check if unknown vendor network has suspicious patterns
     */
    private fun hasSuspiciousPattern(network: NetworkDatabase.NetworkEntry): Boolean {
        // Check for suspicious SSID patterns
        val suspiciousSSIDPatterns = listOf(
            "test", "hack", "pwn", "evil", "rogue", "fake",
            "monitor", "probe", "scan", "attack", "wifi.*pineapple"
        )
        
        val ssidLower = network.ssid.lowercase()
        if (suspiciousSSIDPatterns.any { pattern -> ssidLower.contains(pattern) }) {
            return true
        }
        
        // Check for randomized MAC address patterns (locally administered)
        val macBytes = network.bssid.split(":").take(1).firstOrNull()
        if (macBytes != null && macBytes.length == 2) {
            val firstByte = macBytes.toIntOrNull(16) ?: return false
            // Check if locally administered bit is set (bit 1 of first byte)
            if ((firstByte and 0x02) != 0) {
                return true
            }
        }
        
        // Check for excessive signal strength variations (possible spoofing)
        if (network.signalHistory.size >= 3) {
            val signals = network.signalHistory.takeLast(3).map { it.level }
            val maxVariation = signals.maxOrNull()!! - signals.minOrNull()!!
            if (maxVariation > 30) { // More than 30 dBm variation
                return true
            }
        }
        
        return false
    }
    
    private fun detectKarmaAttacks(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        val commonSSIDs = setOf(
            "Free WiFi", "Public WiFi", "Guest", "WiFi", "Internet",
            "Starbucks", "McDonalds", "Airport WiFi", "Hotel WiFi"
        )
        
        val suspiciousNetworks = networks.filter { network ->
            commonSSIDs.any { commonSSID -> 
                network.ssid.contains(commonSSID, ignoreCase = true) 
            }
        }
        
        if (suspiciousNetworks.size >= 3) {
            anomalies.add(SecurityAnomaly(
                type = AnomalyType.KARMA_ATTACK,
                severity = Severity.HIGH,
                description = "Possible Karma attack: Multiple common/generic SSIDs detected",
                affectedNetworks = suspiciousNetworks.map { it.bssid },
                evidence = mapOf(
                    "suspiciousSSIDs" to suspiciousNetworks.map { it.ssid },
                    "count" to suspiciousNetworks.size
                ),
                recommendedAction = "Avoid connecting to generic network names. Verify legitimacy."
            ))
        }
        
        return anomalies
    }
    
    private fun detectSignalAnomalies(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        networks.forEach { network ->
            val signalHistory = network.signalHistory
            if (signalHistory.size >= 3) {
                val recentSignals = signalHistory.takeLast(3).map { it.level }
                val variance = calculateVariance(recentSignals)
                
                // High signal variance might indicate spoofing or interference
                if (variance > 400) { // dBm² threshold
                    anomalies.add(SecurityAnomaly(
                        type = AnomalyType.SIGNAL_JAMMING,
                        severity = Severity.MEDIUM,
                        description = "Unusual signal pattern for ${network.ssid}: High variance detected",
                        affectedNetworks = listOf(network.bssid),
                        evidence = mapOf(
                            "variance" to variance,
                            "recentSignals" to recentSignals
                        ),
                        recommendedAction = "Monitor network stability. Possible interference or spoofing."
                    ))
                }
            }
        }
        
        return anomalies
    }
    
    private fun detectBeaconFlooding(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        // Group networks by approximate location (if GPS available)
        val locationGroups = networks.filter { network ->
            val lastLocation = network.locations.lastOrNull()
            lastLocation != null && lastLocation.latitude != 0.0 && lastLocation.longitude != 0.0
        }.groupBy { network ->
            val location = network.locations.last()
            // Round to ~100m precision for grouping
            Pair(
                (location.latitude * 1000).toInt(),
                (location.longitude * 1000).toInt()
            )
        }
        
        locationGroups.forEach { (location, networksAtLocation) ->
            if (networksAtLocation.size > 20) { // Threshold for beacon flooding
                anomalies.add(SecurityAnomaly(
                    type = AnomalyType.BEACON_FLOODING,
                    severity = Severity.MEDIUM,
                    description = "Possible beacon flooding: ${networksAtLocation.size} networks at same location",
                    affectedNetworks = networksAtLocation.map { it.bssid },
                    evidence = mapOf(
                        "networkCount" to networksAtLocation.size,
                        "location" to location
                    ),
                    recommendedAction = "Excessive networks detected. Possible attack or testing in progress."
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectSuspiciousOpenNetworks(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        val openNetworks = networks.filter { network -> 
            network.securityTypes.any { secType -> secType.contains("Open", ignoreCase = true) }
        }
        val suspiciousOpenNetworks = openNetworks.filter { network ->
            // Check for suspicious patterns in open networks
            val vendor = vendorLookup.lookupVendor(network.bssid)
            vendor.securityRisk != VendorLookup.SecurityRisk.LOW && 
            network.ssid.isNotEmpty() &&
            !network.ssid.contains("guest", ignoreCase = true)
        }
        
        if (suspiciousOpenNetworks.isNotEmpty()) {
            anomalies.add(SecurityAnomaly(
                type = AnomalyType.CAPTIVE_PORTAL_ATTACK,
                severity = Severity.MEDIUM,
                description = "Suspicious open networks from non-standard vendors detected",
                affectedNetworks = suspiciousOpenNetworks.map { it.bssid },
                evidence = mapOf(
                    "openNetworkCount" to suspiciousOpenNetworks.size,
                    "ssids" to suspiciousOpenNetworks.map { it.ssid }
                ),
                recommendedAction = "Be cautious with open networks from unknown vendors."
            ))
        }
        
        return anomalies
    }
    
    private fun detectChannelAnomalies(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        // Analyze channel distribution
        val channelCounts = networks.mapNotNull { network -> 
            network.signalHistory.lastOrNull()?.frequency 
        }.groupingBy { it }.eachCount()
        val maxChannelCount = channelCounts.maxByOrNull { it.value }?.value ?: 0
        
        // If one channel has > 50% of all networks, might be suspicious
        if (maxChannelCount > networks.size * 0.5 && networks.size > 10) {
            val dominantChannel = channelCounts.maxByOrNull { it.value }?.key
            
            anomalies.add(SecurityAnomaly(
                type = AnomalyType.CHANNEL_HOPPING,
                severity = Severity.MEDIUM,
                description = "Unusual channel concentration: ${maxChannelCount} networks on channel/frequency $dominantChannel",
                evidence = mapOf(
                    "dominantChannel" to (dominantChannel ?: "Unknown"),
                    "networkCount" to maxChannelCount,
                    "percentage" to (maxChannelCount.toFloat() / networks.size * 100).toInt()
                ),
                recommendedAction = "Monitor for coordinated attack or equipment malfunction."
            ))
        }
        
        return anomalies
    }
    
    /**
     * Detect mass unknown vendor attacks (e.g., coordinated fake APs)
     * Only flag if there's an unusual concentration of unknown vendors
     */
    private fun detectMassUnknownVendors(networks: List<NetworkDatabase.NetworkEntry>): List<SecurityAnomaly> {
        val anomalies = mutableListOf<SecurityAnomaly>()
        
        val unknownVendorNetworks = networks.filter { network ->
            val vendor = vendorLookup.lookupVendor(network.bssid)
            vendor.securityRisk == VendorLookup.SecurityRisk.UNKNOWN
        }
        
        val totalNetworks = networks.size
        val unknownCount = unknownVendorNetworks.size
        val unknownPercentage = if (totalNetworks > 0) (unknownCount.toFloat() / totalNetworks * 100) else 0f
        
        // Only flag if > 80% of networks are from unknown vendors AND we have significant count
        if (unknownPercentage > 80f && unknownCount > 10) {
            anomalies.add(SecurityAnomaly(
                type = AnomalyType.VENDOR_ANOMALY,
                severity = Severity.MEDIUM,
                description = "Mass unknown vendor detection: ${unknownCount}/${totalNetworks} networks (${unknownPercentage.toInt()}%) from unrecognized vendors",
                affectedNetworks = unknownVendorNetworks.take(5).map { it.bssid }, // Limit to first 5
                evidence = mapOf(
                    "unknownCount" to unknownCount,
                    "totalCount" to totalNetworks,
                    "percentage" to unknownPercentage,
                    "threshold" to "80%"
                ),
                recommendedAction = "Unusual concentration of unknown vendors detected. Possible coordinated attack or testing environment."
            ))
        }
        
        return anomalies
    }
    
    private fun calculateVariance(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
    
    private fun calculateOverallRisk(anomalies: List<SecurityAnomaly>): RiskLevel {
        val criticalCount = anomalies.count { it.severity == Severity.CRITICAL }
        val highCount = anomalies.count { it.severity == Severity.HIGH }
        val mediumCount = anomalies.count { it.severity == Severity.MEDIUM }
        
        return when {
            criticalCount > 0 -> RiskLevel.DANGER
            highCount >= 2 -> RiskLevel.DANGER
            highCount >= 1 -> RiskLevel.WARNING
            mediumCount >= 3 -> RiskLevel.WARNING
            mediumCount >= 1 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }
    }
    
    private fun generateSummary(anomalies: List<SecurityAnomaly>, networkCount: Int): String {
        return when {
            anomalies.isEmpty() -> "✅ No security anomalies detected in $networkCount networks"
            anomalies.size == 1 -> "⚠️ 1 security anomaly detected in $networkCount networks"
            else -> "🚨 ${anomalies.size} security anomalies detected in $networkCount networks"
        }
    }
}
