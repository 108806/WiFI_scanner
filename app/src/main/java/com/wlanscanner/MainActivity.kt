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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.abs
import org.osmdroid.views.overlay.Marker
import com.wlanscanner.data.NetworkDatabase
import com.wlanscanner.data.WifiNetwork
import android.location.Location
import java.io.File

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
    
    // Continuous scanning - OPTIMIZED for mobile scanning
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private val SCAN_INTERVAL_MS = 1500L // 1.5 seconds - aggressive scanning for mobile use
    
    // Database view components
    private var databaseView: View? = null
    private var detailedNetworkAdapter: DetailedNetworkAdapter? = null
    private var allNetworks = mutableListOf<NetworkDatabase.NetworkEntry>()
    private var filteredNetworks = mutableListOf<NetworkDatabase.NetworkEntry>()
    
    // Sorting state
    private enum class SortMode { ALPHABETICAL, SIGNAL_STRENGTH }
    private var currentSortMode = SortMode.ALPHABETICAL
    
    // Scan view components
    private var scanView: View? = null
    private var scanResultAdapter: ScanResultAdapter? = null
    private var currentScanResults = mutableListOf<WifiNetwork>()

    // Map view components
    private var mapView: View? = null
    private var osmMapView: MapView? = null
    
    // Security components
    private val vendorLookup = VendorLookup()
    private val securityDetector = SecurityAnomalyDetector(vendorLookup)
    private var securityView: View? = null
    
    // GPS optimization - avoid duplicate locations
    private var lastRecordedLocation: Location? = null
    private val MIN_DISTANCE_METERS = 5.0f // Only save location if moved > 5m
    private val MIN_TIME_MS = 3000L // Only save location if > 3 seconds passed

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
        
        // Initialize osmdroid configuration with CACHE OPTIMIZATION
        val osmConfig = Configuration.getInstance()
        osmConfig.load(applicationContext, android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))
        
        // Configure OSM cache for offline usage
        val basePath = File(getExternalFilesDir(null), "osmdroid")
        basePath.mkdirs()
        osmConfig.osmdroidBasePath = basePath
        
        val tileCache = File(basePath, "tiles")
        tileCache.mkdirs()
        osmConfig.osmdroidTileCache = tileCache
        
        // Set cache size and expiration (30 days, 100MB cache)
        osmConfig.tileDownloadThreads = 4
        osmConfig.tileFileSystemCacheMaxBytes = 100L * 1024L * 1024L // 100MB
        osmConfig.expirationOverrideDuration = 1000L * 60L * 60L * 24L * 30L // 30 days
        
        Log.d("MainActivity", "OSM Cache configured: ${tileCache.absolutePath}, max size: 100MB")
        
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
                R.id.navigation_security -> {
                    currentTab = "security"
                    showSecurityTab()
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
        val sortButton = databaseView?.findViewById<Button>(R.id.sortButton)
        val sortIndicator = databaseView?.findViewById<TextView>(R.id.sortIndicator)
        
        detailedNetworkAdapter = DetailedNetworkAdapter(filteredNetworks) { networkEntry ->
            showNetworkDetailsDialog(networkEntry)
        }
        recyclerView?.layoutManager = LinearLayoutManager(this)
        recyclerView?.adapter = detailedNetworkAdapter
        
        // Update sort button text and indicator
        updateSortButtonDisplay(sortButton, sortIndicator)
        
        searchEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterNetworks(s.toString())
            }
        })
        
        sortButton?.setOnClickListener {
            toggleSortMode()
            updateSortButtonDisplay(sortButton, sortIndicator)
            filterNetworks(searchEdit?.text.toString())
        }
        
        exportButton?.setOnClickListener {
            exportDatabase()
        }
        
        clearButton?.setOnClickListener {
            clearDatabase()
        }
    }

    private fun toggleSortMode() {
        currentSortMode = when (currentSortMode) {
            SortMode.ALPHABETICAL -> SortMode.SIGNAL_STRENGTH
            SortMode.SIGNAL_STRENGTH -> SortMode.ALPHABETICAL
        }
    }

    private fun updateSortButtonDisplay(sortButton: Button?, sortIndicator: TextView?) {
        when (currentSortMode) {
            SortMode.ALPHABETICAL -> {
                sortButton?.text = "ABC"
                sortIndicator?.text = "🔤"
                sortIndicator?.visibility = View.VISIBLE
            }
            SortMode.SIGNAL_STRENGTH -> {
                sortButton?.text = "SIG"
                sortIndicator?.text = "📶"
                sortIndicator?.visibility = View.VISIBLE
            }
        }
    }

    private fun sortNetworks(networks: MutableList<NetworkDatabase.NetworkEntry>) {
        when (currentSortMode) {
            SortMode.ALPHABETICAL -> {
                networks.sortBy { 
                    if (it.ssid.isEmpty()) "zzz_${it.bssid}" else it.ssid.lowercase()
                }
            }
            SortMode.SIGNAL_STRENGTH -> {
                networks.sortByDescending { entry ->
                    // Get the strongest signal from history (most recent or highest level)
                    entry.signalHistory.maxOfOrNull { it.level } ?: -100
                }
            }
        }
    }

    private fun showMapTab() {
        scanButton.visibility = View.GONE
        contentFrame.removeAllViews()
        
        if (mapView == null) {
            mapView = LayoutInflater.from(this).inflate(R.layout.map_content, contentFrame, false)
            setupMapView()
        }
        
        contentFrame.addView(mapView)
        
        // Initialize OSM map if not already done
        if (osmMapView == null) {
            osmMapView = mapView?.findViewById<MapView>(R.id.osmMapView)
            setupOsmMap()
        }
        
        refreshMapView()
    }

    private fun setupMapView() {
        val refreshMapButton = mapView?.findViewById<Button>(R.id.refreshMapButton)
        val centerMapButton = mapView?.findViewById<Button>(R.id.centerMapButton)
        
        refreshMapButton?.setOnClickListener {
            refreshMapView()
        }
        
        centerMapButton?.setOnClickListener {
            centerMapOnNetworks()
        }
    }

    private fun setupOsmMap() {
        osmMapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(true)
            setMultiTouchControls(true)
            
            // Set default view to Warsaw, Poland
            val mapController = controller
            mapController.setZoom(12.0)
            val startPoint = GeoPoint(52.2297, 21.0122) // Warsaw coordinates
            mapController.setCenter(startPoint)
            
            // Add a small delay to ensure map is fully initialized before loading markers
            post {
                loadNetworksOnMap()
            }
        }
    }

    private fun refreshMapView() {
        loadNetworksOnMap()
        updateMapStats()
    }

    private fun loadNetworksOnMap() {
        osmMapView?.let { map ->
            // Check if map is properly initialized
            if (map.repository == null) {
                Log.w("MainActivity", "Map not yet fully initialized, skipping marker loading")
                return
            }
            
            // Clear existing markers
            map.overlays.clear()
            
            val networkEntries = networkDatabase.getAllNetworkEntries()
            
            // Group networks by location (with tolerance for GPS precision)
            val locationGroups = groupNetworksByLocation(networkEntries)
            var markersCreated = 0
            var totalNetworksWithLocation = 0
            
            locationGroups.forEach { (location, networks) ->
                totalNetworksWithLocation += networks.size
                
                try {
                    // Create marker for this location group
                    val marker = Marker(map)
                    marker.position = location
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    
                    // Configure marker based on grouped networks
                    configureGroupedMarker(marker, networks, map)
                    
                    map.overlays.add(marker)
                    markersCreated++
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error creating marker for location group: ${e.message}")
                }
            }
            
            map.invalidate() // Refresh the map
            Log.d("MainActivity", "Created $markersCreated markers for $totalNetworksWithLocation networks with GPS coordinates")
        }
    }

    private fun groupNetworksByLocation(networkEntries: List<NetworkDatabase.NetworkEntry>): Map<GeoPoint, List<NetworkDatabase.NetworkEntry>> {
        val locationGroups = mutableMapOf<GeoPoint, MutableList<NetworkDatabase.NetworkEntry>>()
        val tolerance = 0.0001 // ~11 meters tolerance for grouping networks
        
        networkEntries.forEach { entry ->
            val lastLocation = entry.locations.lastOrNull()
            if (lastLocation != null && lastLocation.latitude != 0.0 && lastLocation.longitude != 0.0) {
                val entryLocation = GeoPoint(lastLocation.latitude, lastLocation.longitude)
                
                // Find existing group within tolerance or create new one
                val existingGroup = locationGroups.keys.find { existingLocation ->
                    val latDiff = kotlin.math.abs(existingLocation.latitude - entryLocation.latitude)
                    val lonDiff = kotlin.math.abs(existingLocation.longitude - entryLocation.longitude)
                    latDiff < tolerance && lonDiff < tolerance
                }
                
                if (existingGroup != null) {
                    locationGroups[existingGroup]?.add(entry)
                } else {
                    locationGroups[entryLocation] = mutableListOf(entry)
                }
            }
        }
        
        return locationGroups
    }

    private fun configureGroupedMarker(marker: Marker, networks: List<NetworkDatabase.NetworkEntry>, mapView: MapView) {
        // Sort networks by signal strength (strongest first)
        val sortedNetworks = networks.sortedByDescending { entry ->
            entry.signalHistory.maxByOrNull { it.level }?.level ?: -100
        }
        
        val displayCount = minOf(5, sortedNetworks.size)
        val remainingCount = maxOf(0, sortedNetworks.size - 5)
        
        // Set marker title and icon based on strongest signal
        val strongestNetwork = sortedNetworks.first()
        val strongestSignal = strongestNetwork.signalHistory.maxByOrNull { it.level }?.level ?: -100
        
        // Marker title shows count if multiple networks
        marker.title = if (networks.size == 1) {
            val ssid = if (strongestNetwork.ssid.isEmpty()) "Hidden Network" else strongestNetwork.ssid
            ssid
        } else {
            "${networks.size} WiFi Networks"
        }
        
        // Create detailed snippet with up to 5 networks
        val snippetBuilder = StringBuilder()
        
        sortedNetworks.take(5).forEachIndexed { index, entry ->
            val ssid = if (entry.ssid.isEmpty()) "Hidden Network" else entry.ssid
            val signal = entry.signalHistory.maxByOrNull { it.level }?.level ?: -100
            val scanCount = entry.scanCount
            
            if (index > 0) snippetBuilder.append("\n")
            snippetBuilder.append("${index + 1}. $ssid (${signal}dBm, ${scanCount} scans)")
        }
        
        if (remainingCount > 0) {
            snippetBuilder.append("\n... and $remainingCount more networks")
        }
        
        marker.snippet = snippetBuilder.toString()
        
        // Set marker icon based on strongest signal
        when {
            strongestSignal > -50 -> marker.icon = ContextCompat.getDrawable(mapView.context, android.R.drawable.presence_online)
            strongestSignal > -70 -> marker.icon = ContextCompat.getDrawable(mapView.context, android.R.drawable.presence_away)
            else -> marker.icon = ContextCompat.getDrawable(mapView.context, android.R.drawable.presence_busy)
        }
    }

    private fun centerMapOnNetworks() {
        osmMapView?.let { map ->
            val networkEntries = networkDatabase.getAllNetworkEntries()
            val locations = mutableListOf<GeoPoint>()
            
            networkEntries.forEach { entry ->
                val lastLocation = entry.locations.lastOrNull()
                if (lastLocation != null && lastLocation.latitude != 0.0 && lastLocation.longitude != 0.0) {
                    locations.add(GeoPoint(lastLocation.latitude, lastLocation.longitude))
                }
            }
            
            if (locations.isNotEmpty()) {
                if (locations.size == 1) {
                    // Single location - center and zoom
                    map.controller.animateTo(locations[0])
                    map.controller.setZoom(15.0)
                } else {
                    // Multiple locations - calculate bounds and fit all in view
                    val minLat = locations.minOfOrNull { it.latitude } ?: 0.0
                    val maxLat = locations.maxOfOrNull { it.latitude } ?: 0.0
                    val minLon = locations.minOfOrNull { it.longitude } ?: 0.0
                    val maxLon = locations.maxOfOrNull { it.longitude } ?: 0.0
                    
                    val centerLat = (minLat + maxLat) / 2
                    val centerLon = (minLon + maxLon) / 2
                    val centerPoint = GeoPoint(centerLat, centerLon)
                    
                    // Calculate appropriate zoom level
                    val latDiff = maxLat - minLat
                    val lonDiff = maxLon - minLon
                    val maxDiff = maxOf(latDiff, lonDiff)
                    val zoomLevel = when {
                        maxDiff > 1.0 -> 8.0
                        maxDiff > 0.1 -> 11.0
                        maxDiff > 0.01 -> 13.0
                        else -> 15.0
                    }
                    
                    map.controller.animateTo(centerPoint)
                    map.controller.setZoom(zoomLevel)
                }
            } else {
                Toast.makeText(this, "No networks with GPS coordinates found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMapStats() {
        val mapStatsText = mapView?.findViewById<TextView>(R.id.mapStatsText)
        val networkEntries = networkDatabase.getAllNetworkEntries()
        val networksWithLocation = networkEntries.count { entry ->
            val lastLocation = entry.locations.lastOrNull()
            lastLocation != null && lastLocation.latitude != 0.0 && lastLocation.longitude != 0.0
        }
        
        mapStatsText?.text = "Networks on map: $networksWithLocation"
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
        
        // Apply sorting to filtered results
        sortNetworks(filteredNetworks)
        
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

    // Security Tab Functions
    private fun showSecurityTab() {
        if (securityView == null) {
            securityView = LayoutInflater.from(this).inflate(R.layout.security_content, contentFrame, false)
            setupSecurityView()
        }
        
        contentFrame.removeAllViews()
        contentFrame.addView(securityView)
        
        scanButton.visibility = View.GONE
        
        // Auto-analyze current networks
        analyzeNetworkSecurity()
    }
    
    private fun setupSecurityView() {
        val refreshButton = securityView?.findViewById<Button>(R.id.refreshSecurityButton)
        refreshButton?.setOnClickListener {
            analyzeNetworkSecurity()
        }
    }
    
    private fun analyzeNetworkSecurity() {
        val allNetworks = networkDatabase.getAllNetworkEntries()
        
        if (allNetworks.isEmpty()) {
            updateSecurityDisplay(
                SecurityAnomalyDetector.SecurityReport(
                    anomalies = emptyList(),
                    riskLevel = SecurityAnomalyDetector.RiskLevel.SAFE,
                    summary = "No networks to analyze. Perform a WiFi scan first."
                )
            )
            return
        }
        
        // Run security analysis
        val securityReport = securityDetector.analyzeNetworks(allNetworks)
        updateSecurityDisplay(securityReport)
        updateVendorStatistics(allNetworks)
        
        Log.d("MainActivity", "Security analysis complete: ${securityReport.anomalies.size} anomalies detected")
    }
    
    private fun updateSecurityDisplay(report: SecurityAnomalyDetector.SecurityReport) {
        val riskLevelText = securityView?.findViewById<TextView>(R.id.riskLevelText)
        val summaryText = securityView?.findViewById<TextView>(R.id.securitySummary)
        val anomaliesContainer = securityView?.findViewById<LinearLayout>(R.id.anomaliesContainer)
        val noAnomaliesText = securityView?.findViewById<TextView>(R.id.noAnomaliesText)
        
        // Update risk level with color coding
        riskLevelText?.text = report.riskLevel.name
        riskLevelText?.setTextColor(when (report.riskLevel) {
            SecurityAnomalyDetector.RiskLevel.SAFE -> ContextCompat.getColor(this, android.R.color.holo_green_light)
            SecurityAnomalyDetector.RiskLevel.CAUTION -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            SecurityAnomalyDetector.RiskLevel.WARNING -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            SecurityAnomalyDetector.RiskLevel.DANGER -> ContextCompat.getColor(this, android.R.color.holo_red_light)
        })
        
        // Update summary
        summaryText?.text = report.summary
        
        // Clear previous anomalies
        anomaliesContainer?.removeAllViews()
        
        if (report.anomalies.isEmpty()) {
            noAnomaliesText?.visibility = View.VISIBLE
        } else {
            noAnomaliesText?.visibility = View.GONE
            
            // Add each anomaly as a card
            report.anomalies.forEach { anomaly ->
                addAnomalyCard(anomaliesContainer, anomaly)
            }
        }
    }
    
    private fun addAnomalyCard(container: LinearLayout?, anomaly: SecurityAnomalyDetector.SecurityAnomaly) {
        container ?: return
        
        val cardView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, container, false)
        
        val titleText = cardView.findViewById<TextView>(android.R.id.text1)
        val descriptionText = cardView.findViewById<TextView>(android.R.id.text2)
        
        // Format title with severity and type
        val severityIcon = when (anomaly.severity) {
            SecurityAnomalyDetector.Severity.LOW -> "⚪"
            SecurityAnomalyDetector.Severity.MEDIUM -> "🟡"
            SecurityAnomalyDetector.Severity.HIGH -> "🟠"
            SecurityAnomalyDetector.Severity.CRITICAL -> "🔴"
        }
        
        titleText.text = "$severityIcon ${anomaly.severity.name}: ${anomaly.type.name.replace("_", " ")}"
        titleText.setTextColor(when (anomaly.severity) {
            SecurityAnomalyDetector.Severity.LOW -> ContextCompat.getColor(this, android.R.color.white)
            SecurityAnomalyDetector.Severity.MEDIUM -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            SecurityAnomalyDetector.Severity.HIGH -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            SecurityAnomalyDetector.Severity.CRITICAL -> ContextCompat.getColor(this, android.R.color.holo_red_light)
        })
        
        // Format description
        val description = buildString {
            append(anomaly.description)
            if (anomaly.affectedNetworks.isNotEmpty()) {
                append("\n\nAffected: ${anomaly.affectedNetworks.take(3).joinToString(", ")}")
                if (anomaly.affectedNetworks.size > 3) {
                    append(" and ${anomaly.affectedNetworks.size - 3} more")
                }
            }
            append("\n\nAction: ${anomaly.recommendedAction}")
        }
        
        descriptionText.text = description
        descriptionText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        
        // Add some padding and margin with DARK background
        cardView.setPadding(16, 12, 16, 12)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 8)
        cardView.layoutParams = layoutParams
        // Dark background for anomaly cards
        cardView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_dark))
        
        container.addView(cardView)
    }
    
    private fun updateVendorStatistics(networks: List<NetworkDatabase.NetworkEntry>) {
        val vendorStatsText = securityView?.findViewById<TextView>(R.id.vendorStats)
        
        val vendorCounts = networks.groupingBy { network ->
            vendorLookup.lookupVendor(network.bssid).name
        }.eachCount()
        
        val riskCounts = networks.groupingBy { network ->
            vendorLookup.lookupVendor(network.bssid).securityRisk
        }.eachCount()
        
        val statsText = buildString {
            append("Total Networks: ${networks.size}\n")
            append("Unique Vendors: ${vendorCounts.size}\n")
            append("Risk Distribution: ")
            append("Safe: ${riskCounts[VendorLookup.SecurityRisk.LOW] ?: 0}, ")
            append("Medium: ${riskCounts[VendorLookup.SecurityRisk.MEDIUM] ?: 0}, ")
            append("High: ${riskCounts[VendorLookup.SecurityRisk.HIGH] ?: 0}, ")
            append("Unknown: ${riskCounts[VendorLookup.SecurityRisk.UNKNOWN] ?: 0}\n")
            
            // Top vendors
            val topVendors = vendorCounts.toList().sortedByDescending { it.second }.take(5)
            append("Top Vendors: ")
            append(topVendors.joinToString(", ") { "${it.first} (${it.second})" })
        }
        
        vendorStatsText?.text = statsText
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
        
        // If security tab is visible, refresh security analysis
        if (currentTab == "security") {
            analyzeNetworkSecurity()
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
    
    /**
     * Check if location should be recorded based on distance and time constraints
     * This prevents excessive GPS logging while maintaining accuracy
     */
    private fun shouldRecordLocation(newLocation: Location): Boolean {
        val lastLocation = lastRecordedLocation
        val currentTime = System.currentTimeMillis()
        
        if (lastLocation == null) {
            Log.d("MainActivity", "First location recorded")
            return true
        }
        
        val timeDiff = currentTime - (lastLocation.time)
        val distance = lastLocation.distanceTo(newLocation)
        
        val shouldRecord = timeDiff >= MIN_TIME_MS && distance >= MIN_DISTANCE_METERS
        
        if (shouldRecord) {
            Log.d("MainActivity", "Location recorded: moved ${distance}m in ${timeDiff}ms")
        } else {
            Log.v("MainActivity", "Location skipped: moved ${distance}m in ${timeDiff}ms (min: ${MIN_DISTANCE_METERS}m, ${MIN_TIME_MS}ms)")
        }
        
        return shouldRecord
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

    override fun onResume() {
        super.onResume()
        osmMapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        osmMapView?.onPause()
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
