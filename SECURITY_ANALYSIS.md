# 🛡️ Security Analysis System Documentation

## Overview
The WiFi Scanner now includes an advanced security analysis system that can detect various network anomalies and potential security threats without requiring raw packet access.

## Features Implemented

### 🔍 **Vendor Lookup System**
Identifies device manufacturers based on MAC address OUI (Organizationally Unique Identifier):

#### **Supported Vendors:**
- **Major Router Manufacturers**: Netgear, Cisco, Linksys, D-Link, ASUS, TP-Link
- **Mobile Device Vendors**: Apple, Samsung, Xiaomi
- **IoT/DIY Devices**: ESP32/ESP8266 modules, Raspberry Pi
- **Security Testing Equipment**: Hak5 WiFi Pineapple, Realtek USB adapters

#### **Risk Classification:**
- **LOW**: Well-known, legitimate manufacturers
- **MEDIUM**: IoT devices, unknown vendors that could be used maliciously
- **HIGH**: Known penetration testing equipment
- **UNKNOWN**: Unrecognized vendors

### 🚨 **Security Anomaly Detection**

#### **1. Evil Twin Attack Detection**
- Detects multiple BSSIDs using the same SSID
- Identifies networks with same name but different vendors
- **Severity**: HIGH

#### **2. Rogue Access Point Detection**
- Flags high-risk vendors (penetration testing equipment)
- Detects suspicious MAC address patterns
- Identifies locally administered (randomized) MAC addresses
- **Severity**: HIGH

#### **3. Deauth/Jamming Attack Detection**
- Monitors for mass network disappearance (>50% networks gone)
- Tracks network availability patterns
- **Severity**: CRITICAL

#### **4. Karma Attack Detection**
- Identifies multiple common/generic SSIDs appearing together
- Detects patterns like "Free WiFi", "Public WiFi", "Starbucks"
- **Severity**: HIGH

#### **5. Vendor Anomalies**
- Flags unknown or suspicious vendors
- Detects high-risk equipment manufacturers
- **Severity**: MEDIUM to HIGH

#### **6. Signal Interference Detection**
- Analyzes signal strength variance patterns
- Detects unusual signal fluctuations
- **Severity**: MEDIUM

#### **7. Beacon Flooding Detection**
- Identifies excessive networks in same location (>20 networks)
- Detects coordinated network deployment
- **Severity**: MEDIUM

#### **8. Suspicious Open Networks**
- Flags open networks from non-standard vendors
- Excludes legitimate guest networks
- **Severity**: MEDIUM

#### **9. Channel Anomalies**
- Detects unusual channel concentration (>50% on one channel)
- Identifies potential coordinated attacks
- **Severity**: MEDIUM

### 📊 **Security Dashboard Features**

#### **Risk Level Indicator**
- **SAFE** 🟢: No significant threats detected
- **CAUTION** 🟡: Minor anomalies present
- **WARNING** 🟠: Multiple medium-risk issues
- **DANGER** 🔴: Critical threats detected

#### **Real-time Analysis**
- Automatic analysis after each WiFi scan
- Manual refresh option
- Historical pattern tracking

#### **Vendor Statistics**
- Network count by vendor
- Risk distribution overview
- Top vendor identification

## How to Use

### 1. **Access Security Tab**
- Open WiFi Scanner app
- Navigate to "SECURITY" tab (new 4th tab)
- Click "Refresh" to analyze current networks

### 2. **Interpret Results**
- Check **Risk Level** at the top
- Read **Summary** for quick overview
- Review **Security Anomalies** list for details
- Check **Vendor Statistics** for network composition

### 3. **Respond to Threats**
- Follow **Recommended Actions** for each anomaly
- Avoid connecting to flagged networks
- Monitor for recurring patterns

## Technical Implementation

### **Detection Methods**
Since Android doesn't allow raw packet analysis, the system uses:

1. **Behavioral Analysis**: Network appearance/disappearance patterns
2. **Vendor Intelligence**: MAC address OUI database
3. **Signal Pattern Analysis**: RSSI variance tracking
4. **Temporal Correlation**: Time-based anomaly detection
5. **Geographic Clustering**: Location-based analysis

### **Limitations**
- Cannot detect raw deauth packets (requires packet injection/monitoring)
- Limited to scan result data provided by Android WiFi API
- Some sophisticated attacks may not be detectable
- Requires location data for some analyses

### **False Positive Mitigation**
- Multiple evidence points required for high-severity alerts
- Vendor whitelisting for known legitimate equipment
- Time-based pattern analysis to reduce noise
- Configurable thresholds for different anomaly types

## Security Best Practices

### **When Using Scanner**
1. Always enable location services for better analysis
2. Scan regularly to establish baseline patterns
3. Pay attention to CRITICAL and HIGH severity alerts
4. Verify legitimacy of unknown networks before connecting

### **Network Selection**
1. Avoid networks from unknown/suspicious vendors
2. Be cautious with open networks in public places
3. Verify network authenticity with venue staff
4. Use VPN when connecting to untrusted networks

## Example Scenarios

### **Evil Twin Detection**
```
🟠 HIGH: EVIL_TWIN
Multiple BSSIDs for SSID 'Starbucks_WiFi' from different vendors
Affected: aa:bb:cc:dd:ee:ff, 11:22:33:44:55:66
Vendors: Netgear, Unknown Vendor
Action: Verify legitimate access point. Avoid connecting to suspicious networks.
```

### **Jamming Detection**
```
🔴 CRITICAL: DEAUTH_ATTACK
Possible jamming/deauth attack: 75% of networks disappeared
Missing: 12 networks
Action: Potential WiFi jamming detected. Monitor for continued interference.
```

### **Vendor Risk Alert**
```
🟠 HIGH: VENDOR_ANOMALY
High-risk vendor detected: Hak5 (Evil_AP)
Vendor: Hak5 WiFi Pineapple
Action: Do not connect. Known penetration testing equipment.
```

## Advanced Features

### **Pattern Learning**
- Builds baseline of normal network patterns
- Tracks vendor frequency in your area
- Learns typical signal strength ranges

### **Geographic Correlation**
- Groups networks by GPS coordinates
- Detects unusual concentrations
- Identifies mobile vs fixed installations

### **Temporal Analysis**
- Tracks network appearance/disappearance times
- Identifies recurring patterns
- Detects coordinated activities

---

**Note**: This system provides behavioral analysis only. For complete security assessment, combine with other security tools and practices. Always verify network legitimacy through official channels when in doubt.
