package com.wlanscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.wlanscanner.data.NetworkDatabase
import com.wlanscanner.data.WifiNetwork

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var scanButton: Button
    private lateinit var contentFrame: FrameLayout
    private lateinit var contentText: TextView
    
    private lateinit var wifiManager: WifiManager
    private lateinit var locationManager: LocationManager
    private lateinit var networkDatabase: NetworkDatabase
    
    private var isScanning = false
    private var currentTab = "scan"
    
    // Continuous scanning
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private val SCAN_INTERVAL_MS = 5000L // 5 seconds
    
    // Database view components
    private var databaseView: View? = null
    private var detailedNetworkAdapter: DetailedNetworkAdapter? = null
    private var allNetworks = mutableListOf<NetworkDatabase.NetworkEntry>()
    private var filteredNetworks = mutableListOf<NetworkDatabase.NetworkEntry>()
    
    // Scan view components
    private var scanView: View? = null
    private var scanResultAdapter: ScanResultAdapter? = null
    private var currentScanResults = mutableListOf<WifiNetwork>()

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Permissions required for WiFi scanning", Toast.LENGTH_LONG).show()
        }
    }

    // WiFi scan receiver
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("MainActivity", "WiFi scan receiver triggered")
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            Log.d("MainActivity", "Scan results updated: $success")
            if (success) {
                processScanResults()
            } else {
                Log.d("MainActivity", "Scan failed or no results updated")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_simple)
        
        initializeComponents()
        setupBottomNavigation()
        requestRequiredPermissions()
        
        // Show scan tab by default
        showScanTab()
    }

    private fun initializeComponents() {
        bottomNav = findViewById(R.id.nav_view)
        scanButton = findViewById(R.id.scanButton)
        contentFrame = findViewById(R.id.contentFrame)
        contentText = findViewById(R.id.contentText)
        
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        networkDatabase = NetworkDatabase.getInstance(this)
        
        scanButton.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                startScan()
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_scan -> {
                    currentTab = "scan"
                    showScanTab()
                    true
                }
                R.id.navigation_database -> {
                    currentTab = "database"
                    showDatabaseTab()
                    true
                }
                R.id.navigation_map -> {
                    currentTab = "map"
                    showMapTab()
                    true
                }
                else -> false
            }
        }
    }

    private fun showScanTab() {
        Log.d("MainActivity", "showScanTab() called, currentScanResults.size: ${currentScanResults.size}")
        
        if (scanView == null) {
            scanView = LayoutInflater.from(this).inflate(R.layout.scan_content, contentFrame, false)
            setupScanView()
        }
        
        contentFrame.removeAllViews()
        contentFrame.addView(scanView)
        
        scanButton.visibility = View.VISIBLE
        updateScanButtonText()
        updateScanStatus()
        
        // Refresh scan results display when switching to scan tab
        if (scanResultAdapter != null) {
            Log.d("MainActivity", "Refreshing adapter with ${currentScanResults.size} networks")
            scanResultAdapter?.updateResults(currentScanResults)
        }
    }

    private fun setupScanView() {
        val recyclerView = scanView?.findViewById<RecyclerView>(R.id.scanResultsRecyclerView)
        if (scanResultAdapter == null) {
            Log.d("MainActivity", "Creating new ScanResultAdapter with empty list")
            scanResultAdapter = ScanResultAdapter(mutableListOf()) // Create with empty list, not shared reference
            recyclerView?.layoutManager = LinearLayoutManager(this)
            recyclerView?.adapter = scanResultAdapter
        } else {
            Log.d("MainActivity", "ScanResultAdapter already exists, reusing")
        }
        
        Log.d("MainActivity", "ScanView setup completed. Current results count: ${currentScanResults.size}")
    }

    private fun updateScanStatus() {
        val statusText = scanView?.findViewById<TextView>(R.id.scanStatusText)
        
        // Hide status text if we have scan results, show detailed list instead
        if (currentScanResults.isNotEmpty() && !isScanning) {
            statusText?.visibility = View.GONE
            Log.d("MainActivity", "Hiding status text, showing ${currentScanResults.size} networks in list")
        } else {
            statusText?.visibility = View.VISIBLE
            val message = if (isScanning) {
                "Scanning for WiFi networks..."
            } else {
                "Ready to scan - Tap 'Start Scan' to begin"
            }
            statusText?.text = message
            Log.d("MainActivity", "Showing status: $message")
        }
        
        // Debug log
        Log.d("MainActivity", "currentScanResults.size: ${currentScanResults.size}, adapter item count: ${scanResultAdapter?.itemCount}")
    }

    private fun showDatabaseTab() {
        scanButton.visibility = View.GONE
        
        if (databaseView == null) {
            databaseView = LayoutInflater.from(this).inflate(R.layout.database_content, contentFrame, false)
            setupDatabaseView()
        }
        
        contentFrame.removeAllViews()
        contentFrame.addView(databaseView)
        
        refreshDatabaseView()
    }

    private fun setupDatabaseView() {
        val searchEdit = databaseView?.findViewById<EditText>(R.id.searchEdit)
        val recyclerView = databaseView?.findViewById<RecyclerView>(R.id.networksRecyclerView)
        val exportButton = databaseView?.findViewById<Button>(R.id.exportButton)
        val clearButton = databaseView?.findViewById<Button>(R.id.clearButton)
        
        detailedNetworkAdapter = DetailedNetworkAdapter(filteredNetworks) { networkEntry ->
            showNetworkDetailsDialog(networkEntry)
        }
        recyclerView?.layoutManager = LinearLayoutManager(this)
        recyclerView?.adapter = detailedNetworkAdapter
        
        searchEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterNetworks(s.toString())
            }
        })
        
        exportButton?.setOnClickListener {
            exportDatabase()
        }
        
        clearButton?.setOnClickListener {
            clearDatabase()
        }
    }

    private fun showMapTab() {
        scanButton.visibility = View.GONE
        contentFrame.removeAllViews()
        contentFrame.addView(contentText)
        contentText.text = "MAP Tab\nComing Soon..."
    }

    private fun refreshDatabaseView() {
        allNetworks.clear()
        allNetworks.addAll(networkDatabase.getAllNetworkEntries())
        filterNetworks("")
        
        val statsText = databaseView?.findViewById<TextView>(R.id.statsText)
        val stats = networkDatabase.getNetworkStats()
        statsText?.text = "Networks: ${stats["totalNetworks"]} | Total Scans: ${allNetworks.sumOf { it.scanCount }}"
    }

    private fun filterNetworks(query: String) {
        filteredNetworks.clear()
        
        if (query.isEmpty()) {
            filteredNetworks.addAll(allNetworks)
        } else {
            val searchQuery = query.lowercase()
            filteredNetworks.addAll(allNetworks.filter { network ->
                network.ssid.lowercase().contains(searchQuery) ||
                network.bssid.lowercase().contains(searchQuery)
            })
        }
        
        detailedNetworkAdapter?.notifyDataSetChanged()
    }
    
    private fun exportDatabase() {
        try {
            val fileName = networkDatabase.exportToDownloads()
            Toast.makeText(this, "Exported to Downloads/$fileName\nUse: adb pull /storage/emulated/0/Download/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearDatabase() {
        try {
            networkDatabase.clearAllNetworks()
            currentScanResults.clear()
            refreshDatabaseView()
            if (currentTab == "scan") {
                scanResultAdapter?.updateResults(currentScanResults)
                updateScanStatus()
            }
            Toast.makeText(this, "Database cleared successfully", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Database cleared by user")
        } catch (e: Exception) {
            Toast.makeText(this, "Clear failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScan() {
        Log.d("MainActivity", "startScan() called")
        
        // Request fresh location before scanning
        requestFreshLocation()
        
        if (!hasLocationPermission() || !hasWifiPermissions()) {
            Log.d("MainActivity", "Missing permissions - Location: ${hasLocationPermission()}, WiFi: ${hasWifiPermissions()}")
            requestRequiredPermissions()
            return
        }
        
        // Check if WiFi is enabled
        if (!wifiManager.isWifiEnabled) {
            Log.d("MainActivity", "WiFi is disabled")
            Toast.makeText(this, "Please enable WiFi to scan for networks", Toast.LENGTH_LONG).show()
            return
        }
        
        // Check if location services are enabled
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.d("MainActivity", "Location services are disabled")
            Toast.makeText(this, "Please enable Location Services for accurate GPS coordinates", Toast.LENGTH_LONG).show()
            // Continue scanning even without GPS, but warn user
        } else {
            Log.d("MainActivity", "Location services status - GPS: $isGpsEnabled, Network: $isNetworkEnabled")
        }
        
        isScanning = true
        updateScanButtonText()
        
        // Register receiver
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        // Start continuous scanning
        startContinuousScanning()
        
        if (currentTab == "scan") {
            updateScanStatus()
        }
        
        Toast.makeText(this, "Continuous WiFi scanning started", Toast.LENGTH_SHORT).show()
    }
    
    private fun startContinuousScanning() {
        // Create runnable for continuous scanning
        scanRunnable = object : Runnable {
            override fun run() {
                if (isScanning) {
                    Log.d("MainActivity", "Performing automatic scan")
                    val scanStarted = wifiManager.startScan()
                    Log.d("MainActivity", "Auto scan started: $scanStarted")
                    
                    // Schedule next scan
                    scanHandler.postDelayed(this, SCAN_INTERVAL_MS)
                }
            }
        }
        
        // Start first scan immediately
        val firstScanStarted = wifiManager.startScan()
        Log.d("MainActivity", "Initial scan started: $firstScanStarted")
        
        // Schedule continuous scanning
        scanRunnable?.let { 
            scanHandler.postDelayed(it, SCAN_INTERVAL_MS)
        }
    }

    private fun stopScan() {
        isScanning = false
        updateScanButtonText()
        
        // Stop continuous scanning
        scanRunnable?.let { scanHandler.removeCallbacks(it) }
        scanRunnable = null
        
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Receiver was not registered
        }
        
        if (currentTab == "scan") {
            updateScanStatus()
        }
        
        Toast.makeText(this, "Continuous WiFi scanning stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateScanButtonText() {
        scanButton.text = if (isScanning) "Stop Scan" else "Start Scan"
    }

    private fun processScanResults() {
        Log.d("MainActivity", "processScanResults() called")
        
        if (!hasWifiPermissions() || !hasLocationPermission()) {
            Log.d("MainActivity", "Missing permissions in processScanResults")
            return
        }
        
        val scanResults = wifiManager.scanResults
        Log.d("MainActivity", "Raw scan results count: ${scanResults.size}")
        
        val location = getCurrentLocation()
        
        // Clear and update current scan results for live display
        currentScanResults.clear()
        Log.d("MainActivity", "currentScanResults cleared, size now: ${currentScanResults.size}")
        
        scanResults.forEach { scanResult ->
            Log.d("MainActivity", "Processing network: SSID='${scanResult.SSID}', BSSID='${scanResult.BSSID}', Level=${scanResult.level}")
            
            val wifiNetwork = WifiNetwork(
                ssid = scanResult.SSID ?: "[Hidden Network]",
                bssid = scanResult.BSSID,
                capabilities = scanResult.capabilities,
                frequency = scanResult.frequency,
                level = scanResult.level,
                timestamp = System.currentTimeMillis(),
                latitude = location?.first ?: 0.0,
                longitude = location?.second ?: 0.0,
                address = "" // Address will be geocoded when added to database
            )
            
            currentScanResults.add(wifiNetwork)
            Log.d("MainActivity", "Added to currentScanResults, new size: ${currentScanResults.size}")
            networkDatabase.addOrUpdateNetwork(wifiNetwork)
        }
        
        Log.d("MainActivity", "Final currentScanResults size: ${currentScanResults.size}")
        
        // Update live scan results display
        if (currentTab == "scan") {
            Log.d("MainActivity", "Updating scan results adapter with ${currentScanResults.size} networks")
            scanResultAdapter?.updateResults(currentScanResults)
            updateScanStatus()
        }
        
        // If database tab is visible, refresh it
        if (currentTab == "database") {
            refreshDatabaseView()
        }
    }

    private fun getCurrentLocation(): Pair<Double, Double>? {
        return try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
                
                // Try GPS first
                val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLocation != null && 
                    (System.currentTimeMillis() - gpsLocation.time) < 300000) { // 5 minutes old
                    Log.d("MainActivity", "Using GPS location: ${gpsLocation.latitude}, ${gpsLocation.longitude}, accuracy: ${gpsLocation.accuracy}m")
                    return Pair(gpsLocation.latitude, gpsLocation.longitude)
                }
                
                // Fallback to network location
                val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (networkLocation != null && 
                    (System.currentTimeMillis() - networkLocation.time) < 600000) { // 10 minutes old
                    Log.d("MainActivity", "Using network location: ${networkLocation.latitude}, ${networkLocation.longitude}, accuracy: ${networkLocation.accuracy}m")
                    return Pair(networkLocation.latitude, networkLocation.longitude)
                }
                
                Log.w("MainActivity", "No recent location available")
                null
            } else {
                Log.w("MainActivity", "Location permission not granted")
                null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get current location", e)
            null
        }
    }

    private fun requestFreshLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
                
                Log.d("MainActivity", "Requesting fresh GPS location...")
                
                // Request single location update
                locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    { location ->
                        Log.d("MainActivity", "Fresh GPS location received: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
                    },
                    mainLooper
                )
                
                // Also request network location as backup
                locationManager.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER,
                    { location ->
                        Log.d("MainActivity", "Fresh network location received: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
                    },
                    mainLooper
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to request fresh location", e)
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWifiPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CHANGE_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showNetworkDetailsDialog(networkEntry: NetworkDatabase.NetworkEntry) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_network_details, null)
        
        // Populate dialog fields
        val ssidText = dialogView.findViewById<TextView>(R.id.dialogSsidText)
        val bssidText = dialogView.findViewById<TextView>(R.id.dialogBssidText)
        val frequencyText = dialogView.findViewById<TextView>(R.id.dialogFrequencyText)
        val securityText = dialogView.findViewById<TextView>(R.id.dialogSecurityText)
        val signalText = dialogView.findViewById<TextView>(R.id.dialogSignalText)
        val scanCountText = dialogView.findViewById<TextView>(R.id.dialogScanCountText)
        val firstSeenText = dialogView.findViewById<TextView>(R.id.dialogFirstSeenText)
        val lastSeenText = dialogView.findViewById<TextView>(R.id.dialogLastSeenText)
        val locationText = dialogView.findViewById<TextView>(R.id.dialogLocationText)
        val anomaliesText = dialogView.findViewById<TextView>(R.id.dialogAnomaliesText)
        val signalHistoryText = dialogView.findViewById<TextView>(R.id.dialogSignalHistoryText)
        
        ssidText.text = if (networkEntry.ssid.isNotEmpty()) networkEntry.ssid else "Hidden Network"
        bssidText.text = "BSSID: ${networkEntry.bssid}"
        
        // Get latest signal reading for frequency and level
        val latestSignal = networkEntry.signalHistory.maxByOrNull { it.timestamp }
        if (latestSignal != null) {
            val channel = when {
                latestSignal.frequency >= 2412 && latestSignal.frequency <= 2484 -> {
                    // 2.4 GHz band
                    if (latestSignal.frequency == 2484) 14 else (latestSignal.frequency - 2412) / 5 + 1
                }
                latestSignal.frequency >= 5170 && latestSignal.frequency <= 5825 -> {
                    // 5 GHz band
                    (latestSignal.frequency - 5000) / 5
                }
                latestSignal.frequency >= 5955 && latestSignal.frequency <= 7115 -> {
                    // 6 GHz band  
                    (latestSignal.frequency - 5950) / 5
                }
                else -> 0 // Unknown
            }
            frequencyText.text = "Frequency: ${latestSignal.frequency} MHz (Channel $channel)"
            
            val signalPercentage = when {
                latestSignal.level >= -30 -> 100
                latestSignal.level >= -40 -> 90
                latestSignal.level >= -50 -> 80
                latestSignal.level >= -60 -> 70
                latestSignal.level >= -70 -> 60
                latestSignal.level >= -80 -> 50
                latestSignal.level >= -90 -> 30
                else -> 10
            }
            signalText.text = "Signal: ${latestSignal.level} dBm (${signalPercentage}%)"
        } else {
            frequencyText.text = "Frequency: Unknown"
            signalText.text = "Signal: Unknown"
        }
        
        securityText.text = "Security: ${networkEntry.securityTypes.joinToString(", ")}"
        
        scanCountText.text = "Scan Count: ${networkEntry.scanCount}"
        
        val firstSeenFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(networkEntry.firstSeen))
        firstSeenText.text = "First Seen: $firstSeenFormatted"
        
        val timeDiff = System.currentTimeMillis() - networkEntry.lastSeen
        val timeAgo = when {
            timeDiff < 60000 -> "${timeDiff / 1000}s ago"
            timeDiff < 3600000 -> "${timeDiff / 60000}m ago"
            else -> "${timeDiff / 3600000}h ago"
        }
        lastSeenText.text = "Last Seen: $timeAgo"
        
        // Get latest location and address
        val latestLocation = networkEntry.locations.maxByOrNull { it.timestamp }
        if (latestLocation != null && latestLocation.latitude != 0.0 && latestLocation.longitude != 0.0) {
            val coordinatesText = "Coordinates: ${String.format("%.6f", latestLocation.latitude)}, ${String.format("%.6f", latestLocation.longitude)}"
            val addressText = if (!networkEntry.address.isNullOrEmpty()) {
                "\nAddress: ${networkEntry.address}"
            } else {
                "\nAddress: Not available"
            }
            locationText.text = coordinatesText + addressText
        } else {
            locationText.text = "Location: Not available"
        }
        
        if (networkEntry.anomalies.isNotEmpty()) {
            anomaliesText.text = networkEntry.anomalies.joinToString(", ")
            anomaliesText.setTextColor(ContextCompat.getColor(this, R.color.warning_color))
        } else {
            anomaliesText.text = "None detected"
        }
        
        // Calculate signal statistics
        val signalLevels = networkEntry.signalHistory.map { it.level }
        if (signalLevels.isNotEmpty()) {
            val min = signalLevels.minOrNull() ?: 0
            val max = signalLevels.maxOrNull() ?: 0
            val avg = signalLevels.average().toInt()
            signalHistoryText.text = "Min: ${min} dBm, Max: ${max} dBm, Avg: ${avg} dBm"
        } else {
            signalHistoryText.text = "No signal history available"
        }
        
        AlertDialog.Builder(this)
            .setTitle("Network Details")
            .setView(dialogView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) {
            // Stop continuous scanning
            scanRunnable?.let { scanHandler.removeCallbacks(it) }
            scanRunnable = null
            
            try {
                unregisterReceiver(wifiScanReceiver)
            } catch (e: Exception) {
                // Receiver was not registered
            }
        }
    }
}
