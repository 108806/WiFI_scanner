package com.wlanscanner

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WifiScanViewModel : ViewModel() {
    
    private val _scanResults = MutableLiveData<List<WifiNetwork>>()
    val scanResults: LiveData<List<WifiNetwork>> = _scanResults
    
    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning
    
    private val allResults = mutableListOf<WifiNetwork>()
    
    init {
        _scanResults.value = emptyList()
        _isScanning.value = false
    }
    
    fun addScanResults(networks: List<WifiNetwork>) {
        allResults.addAll(networks)
        _scanResults.value = allResults.toList()
    }
    
    fun clearResults() {
        allResults.clear()
        _scanResults.value = emptyList()
    }
    
    fun startScanning() {
        _isScanning.value = true
    }
    
    fun stopScanning() {
        _isScanning.value = false
    }
    
    fun getUniqueNetworks(): List<WifiNetwork> {
        val uniqueNetworks = mutableMapOf<String, WifiNetwork>()
        allResults.forEach { network ->
            val key = "${network.ssid}_${network.bssid}"
            if (!uniqueNetworks.containsKey(key) || 
                network.timestamp > uniqueNetworks[key]!!.timestamp) {
                uniqueNetworks[key] = network
            }
        }
        return uniqueNetworks.values.toList()
    }
    
    fun getNetworksBySSID(ssid: String): List<WifiNetwork> {
        return allResults.filter { it.ssid == ssid }
    }
    
    fun getNetworksByBSSID(bssid: String): List<WifiNetwork> {
        return allResults.filter { it.bssid == bssid }
    }
}
