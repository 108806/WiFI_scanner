# Android WiFi Scanner

A comprehensive Android application that scans for WiFi networks and logs detailed information about detected access points with advanced anomaly detection and dark theme interface. The app captures essential data including SSID, BSSID, MAC address, GPS coordinates, signal strength, and provides security analysis with evil twin detection.

## Features

### 🔍 **Advanced WiFi Network Detection**
- **Comprehensive Scanning**: Detects all available WiFi networks in range
- **Persistent Database**: Stores network history with BSSID_hex(SSID) keys
- **Real-time Anomaly Detection**: Identifies security threats and suspicious networks
- **Evil Twin Detection**: Spots networks with same SSID but different BSSID in proximity

### 📊 **Detailed Data Logging**
- **SSID (Network Name)** - WiFi network identifier
- **BSSID (MAC Address)** - Unique access point identifier  
- **Signal Strength** - dBm values and quality percentage
- **Frequency and Band** - 2.4GHz, 5GHz, 6GHz detection
- **Security Analysis** - Open, WEP, WPA, WPA2, WPA3 with vulnerability assessment
- **GPS Coordinates** - Latitude, Longitude, Altitude with accuracy
- **Timestamp History** - Complete scan timeline
- **Location Tracking** - Movement detection for access points

### 🚨 **Security & Anomaly Detection**
- **Open Network Alerts** - Critical security warnings for unencrypted networks
- **Weak Encryption Detection** - Flags deprecated WEP and outdated WPA
- **Security Downgrade Alerts** - Detects WPA3→WPA2 or WPA2→WPA downgrades  
- **Signal Anomalies** - Unusually strong signals or high variance detection
- **Frequency Changes** - Monitors access point frequency shifts
- **Location Mismatches** - Alerts when networks move unexpectedly
- **Evil Twin Detection** - Identifies potential fake access points

### 🌚 **Dark Theme Interface**
- **Professional Dark Design** - Black (#121212) background with high contrast
- **Color-Coded Indicators** - Signal strength and security level visualization
- **Anomaly Highlighting** - Red/Yellow/Blue alerts for different threat levels
- **Material Design** - Modern Android UI components

### 💾 **Advanced Export System**
- **Dual JSON Export** - Separate files for current scan and full database
- **Network History** - Complete timeline of all detected networks
- **Anomaly Reports** - Security findings and threat analysis
- **Statistical Summary** - Network counts, anomaly statistics, security overview
- **Device Information** - Samsung Galaxy A55 5G metadata and Android version

### 🎨 **Visual Features**
- **Signal Strength Bars** - Color-coded from green (excellent) to red (poor)
- **Security Badges** - Visual indicators for encryption types
- **Anomaly Warnings** - Prominent alerts for security issues
- **Network Cards** - Clean, organized display of network information
- **Real-time Updates** - Live scanning with immediate results

## Target Device

Optimized for **Samsung Galaxy A55 5G** and can be tested via USB debugging.

## Requirements

- Android 7.0 (API level 24) or higher
- Location permissions for WiFi scanning
- WiFi enabled on device

## Permissions

The app requires the following permissions:

- `ACCESS_WIFI_STATE` - Access WiFi network information
- `CHANGE_WIFI_STATE` - Enable WiFi scanning
- `ACCESS_FINE_LOCATION` - Required for WiFi scanning on Android 6+
- `ACCESS_COARSE_LOCATION` - Location services
- `ACCESS_BACKGROUND_LOCATION` - Background location (Android 10+)
- `WRITE_EXTERNAL_STORAGE` - Save JSON files (Android 9 and below)
- `FOREGROUND_SERVICE` - Background scanning service

## Installation & Setup

### Prerequisites

1. **Android Studio** (latest stable version)
2. **Android SDK** with minimum API 24
3. **USB Debugging enabled** on your Galaxy A55 5G
4. **Developer Options enabled** on the device

### Building the Project

1. Clone or download the project
2. Open in Android Studio
3. Wait for Gradle sync to complete
4. Connect your Galaxy A55 5G via USB
5. Enable USB debugging when prompted
6. Build and run the project

### Using Android Studio

```bash
# Build the project
./gradlew build

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

## Usage

### Basic Operation

1. **Launch the app** on your Galaxy A55 5G
2. **Grant permissions** when prompted:
   - Location access (required for WiFi scanning)
   - Storage access (for saving JSON files)
3. **Enable WiFi** if not already enabled
4. **Tap "Start Scan"** to begin scanning for networks
5. **View results** in the list as they appear
6. **Export to JSON** to save scan data
7. **Clear results** to start fresh

### Understanding the Results

Each detected network shows:
- **Network Name** (SSID) - The WiFi network identifier
- **BSSID** - Unique MAC address of the access point
- **Signal Strength** - Both dBm value and quality percentage
- **Frequency** - Operating frequency and band
- **Security** - Encryption type used
- **Location** - GPS coordinates where detected
- **Timestamp** - When the network was scanned

### JSON Export Format

The exported JSON file contains:

```json
{
  "scan_info": {
    "total_scans": 25,
    "unique_networks": 12,
    "export_timestamp": "2025-01-07 14:30:15",
    "device_info": {
      "model": "SM-A556E",
      "manufacturer": "samsung",
      "android_version": "14"
    }
  },
  "networks": {
    "aa:bb:cc:dd:ee:ff": [
      {
        "ssid": "MyNetwork",
        "bssid": "aa:bb:cc:dd:ee:ff",
        "capabilities": "[WPA2-PSK-CCMP][ESS]",
        "frequency": 2412,
        "level": -45,
        "timestamp": 1704632415000,
        "latitude": 40.7128,
        "longitude": -74.0060,
        "altitude": 10.5,
        "accuracy": 3.0
      }
    ]
  }
}
```

## Development

### Project Structure

```
app/src/main/java/com/wlanscanner/
├── MainActivity.kt           # Main UI and scan coordination
├── WifiNetwork.kt           # Data model for WiFi networks
├── WifiScanViewModel.kt     # ViewModel for managing scan data
├── WifiNetworkAdapter.kt    # RecyclerView adapter for displaying results
└── WifiScanService.kt       # Background service for continuous scanning
```

### Key Components

- **MainActivity**: Handles UI interactions, permissions, and scan orchestration
- **WifiNetwork**: Data class representing a detected WiFi network
- **WifiScanViewModel**: Manages scan results and UI state
- **WifiNetworkAdapter**: Displays scan results in a RecyclerView
- **WifiScanService**: Optional background service for continuous monitoring

### Development Commands

#### Gradle Build Commands

```bash
# Clean project (removes all build artifacts)
./gradlew clean

# Build debug APK only
./gradlew assembleDebug

# Build release APK only
./gradlew assembleRelease

# Build all variants (debug and release)
./gradlew build

# Install debug APK on connected device
./gradlew installDebug

# Install release APK on connected device
./gradlew installRelease

# Run unit tests
./gradlew test

# Run instrumented tests on connected device
./gradlew connectedAndroidTest

# Clean, build and install in one command
./gradlew clean build installDebug

# Generate Gradle wrapper (if missing)
gradle wrapper
```

#### ADB (Android Debug Bridge) Commands

```bash
# Check ADB version and installation
adb version

# List all connected devices
adb devices

# Check if specific package is installed
adb shell pm list packages | findstr wlanscanner

# Install APK manually
adb install app/build/outputs/apk/debug/app-debug.apk

# Uninstall the app
adb uninstall com.wlanscanner

# Start the main activity
adb shell am start -n com.wlanscanner/.MainActivity

# Stop the app
adb shell am force-stop com.wlanscanner

# View app logs (filter by tag)
adb logcat | Select-String "WifiScanner"

# View all logs from the app
adb logcat | Select-String "com.wlanscanner"

# Clear app data
adb shell pm clear com.wlanscanner

# Grant permissions manually
adb shell pm grant com.wlanscanner android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.wlanscanner android.permission.ACCESS_COARSE_LOCATION

# Check granted permissions
adb shell dumpsys package com.wlanscanner | Select-String "permission"

# Pull exported JSON file from device
adb pull /storage/emulated/0/Download/wifi_scan_*.json ./exports/

# Take screenshot of device
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./

# Restart ADB server (if connection issues)
adb kill-server
adb start-server
```

#### VS Code Tasks

The project includes predefined tasks in `.vscode/tasks.json`:

```bash
# Build Android App
Ctrl+Shift+P → "Tasks: Run Task" → "Build Android App"

# Install Debug APK
Ctrl+Shift+P → "Tasks: Run Task" → "Install Debug APK"

# Clean Project
Ctrl+Shift+P → "Tasks: Run Task" → "Clean Project"

# Run Tests
Ctrl+Shift+P → "Tasks: Run Task" → "Run Tests"

# Build and Install (combined)
Ctrl+Shift+P → "Tasks: Run Task" → "Build and Install"
```

### Setup Requirements

#### Android SDK Setup

1. **Install Android SDK** or Android Studio
2. **Create local.properties** file in project root:
   ```properties
   sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   ```
3. **Add ADB to PATH** (Windows):
   ```
   C:\Users\YourUsername\AppData\Local\Android\Sdk\platform-tools
   ```

#### PowerShell Setup (Windows)

```powershell
# Add ADB to current session PATH
$env:PATH += ";C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools"

# Permanently add to user PATH
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools", [EnvironmentVariableTarget]::User)

# Verify ADB is accessible
adb version
```

### Testing on Galaxy A55 5G

1. **Enable Developer Options**:
   - Go to Settings > About phone
   - Tap "Build number" 7 times
   - Go back to Settings > Developer options

2. **Enable USB Debugging**:
   - In Developer options, enable "USB debugging"
   - Connect device to computer via USB
   - Allow USB debugging when prompted

3. **Install and Test**:
   - Run the app from Android Studio or use: `./gradlew installDebug`
   - Test all features including scanning and JSON export
   - Verify GPS accuracy and signal strength readings

### Development Workflow Examples

#### Quick Development Cycle

```bash
# 1. Check device connection
adb devices

# 2. Clean and build
./gradlew clean assembleDebug

# 3. Install on device
./gradlew installDebug

# 4. Launch app
adb shell am start -n com.wlanscanner/.MainActivity

# 5. Monitor logs
adb logcat | Select-String "WifiScanner"
```

#### Testing & Debugging Workflow

```bash
# 1. Clear app data for fresh test
adb shell pm clear com.wlanscanner

# 2. Grant permissions manually (if needed)
adb shell pm grant com.wlanscanner android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.wlanscanner android.permission.ACCESS_COARSE_LOCATION

# 3. Install and launch
./gradlew installDebug
adb shell am start -n com.wlanscanner/.MainActivity

# 4. Test scan and export, then pull files
adb pull /storage/emulated/0/Download/wifi_scan_*.json ./test_exports/

# 5. View exported data
cat ./test_exports/wifi_scan_*.json | jq .
```

#### Release Build Workflow

```bash
# 1. Clean project
./gradlew clean

# 2. Run tests
./gradlew test

# 3. Build release APK
./gradlew assembleRelease

# 4. Sign APK (if configured)
# APK will be in: app/build/outputs/apk/release/app-release.apk

# 5. Test release version
adb install app/build/outputs/apk/release/app-release.apk
```

#### Code Changes Workflow

```bash
# 1. Make code changes in your IDE
# 2. Quick incremental build and install
./gradlew installDebug

# 3. If issues, do full clean build
./gradlew clean build installDebug

# 4. Monitor for crashes or issues
adb logcat | Select-String "AndroidRuntime|WifiScanner"
```

### Development Troubleshooting

#### Gradle Issues

1. **"Could not find or load main class org.gradle.wrapper.GradleWrapperMain"**:
   ```bash
   # Generate missing Gradle wrapper
   gradle wrapper
   # Or download and extract Gradle manually, then run:
   ./gradle-8.9/bin/gradle.bat wrapper
   ```

2. **"SDK location not found"**:
   ```bash
   # Create local.properties file with your Android SDK path
   echo "sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk" > local.properties
   ```

3. **Build fails with "resource mipmap/ic_launcher not found"**:
   ```bash
   # Use system icons in AndroidManifest.xml:
   android:icon="@android:drawable/ic_dialog_info"
   android:roundIcon="@android:drawable/ic_dialog_info"
   ```

#### ADB Issues

1. **"'adb' is not recognized as a command"**:
   ```bash
   # Add Android SDK platform-tools to PATH:
   $env:PATH += ";C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools"
   ```

2. **"List of devices attached" shows empty**:
   ```bash
   # Enable USB debugging on device
   # Trust computer when prompted
   # Try different USB cable or port
   adb kill-server
   adb start-server
   adb devices
   ```

3. **"Permission denied" or "Shell does not have permission"**:
   ```bash
   # This is normal for some queries, use app-specific commands:
   adb shell am start -n com.wlanscanner/.MainActivity
   ```

#### Device Connection Issues

1. **Device not detected**:
   - Enable Developer Options (tap Build Number 7 times)
   - Enable USB Debugging in Developer Options
   - Install device drivers if on Windows
   - Try different USB cable (data cable, not charging-only)

2. **"Unauthorized device"**:
   - Check device screen for authorization dialog
   - Click "Always allow from this computer"
   - If no dialog appears: `adb kill-server && adb start-server`

3. **App installation fails**:
   ```bash
   # Clear previous installation
   adb uninstall com.wlanscanner
   # Try installing again
   ./gradlew installDebug
   ```

## Troubleshooting

### Common Issues

1. **Location Permission Denied**:
   - Manually grant location permission in Settings > Apps > WiFi Scanner > Permissions
   - Ensure location services are enabled on the device

2. **No Networks Found**:
   - Verify WiFi is enabled
   - Check that location services are working
   - Try scanning in an area with known WiFi networks

3. **Export Failed**:
   - Check storage permissions
   - Ensure sufficient storage space
   - Try exporting to a different location

4. **Background Location Access**:
   - For Android 10+, manually grant background location permission
   - Required for continuous scanning features

### Performance Tips

- **Battery Optimization**: Disable battery optimization for the app for continuous scanning
- **Location Accuracy**: Use high accuracy location mode for better GPS data
- **Scan Frequency**: Avoid excessive scanning to preserve battery life

## Privacy & Security

- **Local Data Only**: All scan data is stored locally on the device
- **No Network Transmission**: No data is sent to external servers
- **User Control**: Users control when scans occur and data export
- **Permission Transparency**: Clear permission requests with explanations

## Contributing

To contribute to this project:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly on Galaxy A55 5G
5. Submit a pull request

## License

This project is for educational and testing purposes. Please respect local regulations regarding WiFi scanning and data collection.

## Support

For issues specific to Galaxy A55 5G testing or general app functionality, please check the troubleshooting section or create an issue in the project repository.
