package com.wlanscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wlanscanner.databinding.ActivityMainBinding
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adapter: WifiNetworkAdapter
    private lateinit var viewModel: WifiScanViewModel
    private lateinit var networkDatabase: NetworkDatabase
    
    private var currentLocation: Location? = null
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val WIFI_PERMISSION_REQUEST_CODE = 1002
        private const val TAG = "WifiScanner"
    }
    
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeComponents()
        setupRecyclerView()
        setupClickListeners()
        requestPermissions()
    }
    
    private fun initializeComponents() {
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        viewModel = ViewModelProvider(this)[WifiScanViewModel::class.java]
        networkDatabase = NetworkDatabase(this)
        
        // Observe scan results
        viewModel.scanResults.observe(this) { results ->
            adapter.updateResults(results)
            binding.tvScanCount.text = "Networks found: ${results.size}"
            
            // Update database with new networks and detect anomalies
            results.forEach { network ->
                val anomalies = networkDatabase.addOrUpdateNetwork(network)
                if (anomalies.isNotEmpty()) {
                    Log.d(TAG, "Anomalies detected for ${network.ssid}: ${anomalies.size}")
                }
            }
            
            // Show statistics
            val stats = networkDatabase.getNetworkStats()
            val statsText = "Networks: ${stats["totalNetworks"]}, " +
                    "Anomalies: ${stats["totalAnomalies"]}, " +
                    "Critical: ${stats["criticalAnomalies"]}"
            Log.d(TAG, "Database stats: $statsText")
        }
        
        // Observe scan status
        viewModel.isScanning.observe(this) { isScanning ->
            binding.btnScan.text = if (isScanning) "Scanning..." else "Start Scan"
            binding.btnScan.isEnabled = !isScanning
            binding.progressBar.visibility = if (isScanning) 
                android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    
    private fun setupRecyclerView() {
        adapter = WifiNetworkAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            if (hasRequiredPermissions()) {
                getCurrentLocationAndScan()
            } else {
                requestPermissions()
            }
        }
        
        binding.btnExport.setOnClickListener {
            exportToJson()
        }
        
        binding.btnClear.setOnClickListener {
            viewModel.clearResults()
            networkDatabase.clearDatabase()
            Toast.makeText(this, "Scan results and database cleared", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val locationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val wifiPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        return locationPermission && wifiPermission
    }
    
    private fun requestPermissions() {
        // First, request basic location permissions (essential for WiFi scanning)
        val basicPermissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            basicPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (basicPermissions.isNotEmpty()) {
            // Show explanation before requesting permissions
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Location Permission Required")
                .setMessage("This app needs location access to scan for WiFi networks. This is required by Android for WiFi scanning.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this, 
                        basicPermissions.toTypedArray(), 
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this, "Location permission is required for WiFi scanning", Toast.LENGTH_LONG).show()
                }
                .show()
        } else {
            // Basic permissions granted, check for background location (optional)
            requestBackgroundLocationIfNeeded()
        }
    }
    
    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Background Location (Optional)")
                    .setMessage("For continuous scanning in background, grant background location access in the next dialog.")
                    .setPositiveButton("Continue") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this, 
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 
                            LOCATION_PERMISSION_REQUEST_CODE + 1
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        Toast.makeText(this, "Background scanning not available", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }
    
    private fun getCurrentLocationAndScan() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
            return
        }
        
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                currentLocation = location
                startWifiScan()
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get location", it)
                // Continue with scan without location
                startWifiScan()
            }
    }
    
    private fun startWifiScan() {
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please enable WiFi to scan", Toast.LENGTH_LONG).show()
            return
        }
        
        viewModel.startScanning()
        
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)
        
        val success = wifiManager.startScan()
        if (!success) {
            scanFailure()
        }
    }
    
    private fun scanSuccess() {
        val results = wifiManager.scanResults
        val wifiNetworks = mutableListOf<WifiNetwork>()
        
        for (scanResult in results) {
            val network = WifiNetwork(
                ssid = scanResult.SSID,
                bssid = scanResult.BSSID,
                capabilities = scanResult.capabilities,
                frequency = scanResult.frequency,
                level = scanResult.level,
                timestamp = System.currentTimeMillis(),
                latitude = currentLocation?.latitude,
                longitude = currentLocation?.longitude,
                altitude = currentLocation?.altitude,
                accuracy = currentLocation?.accuracy?.toDouble()
            )
            wifiNetworks.add(network)
        }
        
        viewModel.addScanResults(wifiNetworks)
        viewModel.stopScanning()
        
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        Toast.makeText(this, "Scan completed: ${wifiNetworks.size} networks found", 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun scanFailure() {
        viewModel.stopScanning()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        Toast.makeText(this, "Scan failed", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportToJson() {
        val results = viewModel.scanResults.value ?: emptyList()
        if (results.isEmpty()) {
            Toast.makeText(this, "No scan results to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            
            // Export current scan results
            val scanFileName = "wifi_scan_$timestamp.json"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val scanFile = File(downloadsDir, scanFileName)
            
            // Create a map grouped by BSSID for easier lookup
            val networksMap = mutableMapOf<String, MutableList<WifiNetwork>>()
            results.forEach { network ->
                val bssid = network.bssid
                if (networksMap.containsKey(bssid)) {
                    networksMap[bssid]?.add(network)
                } else {
                    networksMap[bssid] = mutableListOf(network)
                }
            }
            
            val stats = networkDatabase.getNetworkStats()
            val scanJsonData = mapOf(
                "scan_info" to mapOf(
                    "total_scans" to results.size,
                    "unique_networks" to networksMap.size,
                    "export_timestamp" to dateFormat.format(Date()),
                    "device_info" to mapOf(
                        "model" to Build.MODEL,
                        "manufacturer" to Build.MANUFACTURER,
                        "android_version" to Build.VERSION.RELEASE
                    ),
                    "database_stats" to stats
                ),
                "networks" to networksMap
            )
            
            FileWriter(scanFile).use { writer ->
                gson.toJson(scanJsonData, writer)
            }
            
            // Export full database with anomaly history
            val dbFileName = "wifi_database_$timestamp.json"
            val dbFile = File(downloadsDir, dbFileName)
            val databaseJson = networkDatabase.exportToJson()
            dbFile.writeText(databaseJson)
            
            Toast.makeText(this, "Exported:\n- Scan: ${scanFile.name}\n- Database: ${dbFile.name}", Toast.LENGTH_LONG).show()
            Log.i(TAG, "WiFi scan results exported to: ${scanFile.absolutePath}")
            Log.i(TAG, "WiFi database exported to: ${dbFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export results", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Location permissions granted! You can now scan for WiFi networks.", Toast.LENGTH_SHORT).show()
                    // Optionally ask for background location
                    requestBackgroundLocationIfNeeded()
                } else {
                    // Check which permissions were denied
                    val deniedPermissions = permissions.filterIndexed { index, _ -> 
                        grantResults[index] != PackageManager.PERMISSION_GRANTED 
                    }
                    
                    if (deniedPermissions.any { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION }) {
                        // Show dialog explaining why permission is needed
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("Location permission is required for WiFi scanning on Android. Please grant it in Settings > Apps > WiFi Scanner > Permissions")
                            .setPositiveButton("Open Settings") { _, _ ->
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = android.net.Uri.fromParts("package", packageName, null)
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE + 1 -> {
                // Background location permission result
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Background location granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Background scanning not available", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Receiver was not registered
        }
    }
}
