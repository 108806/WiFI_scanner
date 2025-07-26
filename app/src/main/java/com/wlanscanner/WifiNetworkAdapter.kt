package com.wlanscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wlanscanner.databinding.ItemWifiNetworkBinding
import java.text.SimpleDateFormat
import java.util.*

class WifiNetworkAdapter : RecyclerView.Adapter<WifiNetworkAdapter.WifiNetworkViewHolder>() {
    
    private var networks = listOf<WifiNetwork>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun updateResults(newNetworks: List<WifiNetwork>) {
        networks = newNetworks
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiNetworkViewHolder {
        val binding = ItemWifiNetworkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WifiNetworkViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: WifiNetworkViewHolder, position: Int) {
        holder.bind(networks[position])
    }
    
    override fun getItemCount(): Int = networks.size
    
    inner class WifiNetworkViewHolder(
        private val binding: ItemWifiNetworkBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(network: WifiNetwork) {
            binding.apply {
                // SSID and Security
                tvSsid.text = if (network.ssid.isNotEmpty()) network.ssid else "<Hidden Network>"
                
                val securityType = network.getSecurityType()
                tvSecurity.text = securityType
                
                // Set security badge color
                val securityColor = when (securityType) {
                    "Open" -> ContextCompat.getColor(itemView.context, R.color.anomaly_critical)
                    "WEP" -> ContextCompat.getColor(itemView.context, R.color.anomaly_warning)
                    "WPA" -> ContextCompat.getColor(itemView.context, R.color.anomaly_warning)
                    else -> ContextCompat.getColor(itemView.context, R.color.security_background)
                }
                tvSecurity.setBackgroundColor(securityColor)
                
                // BSSID and Location
                tvBssid.text = "BSSID: ${network.bssid}"
                tvLocation.text = network.getLocationString()
                
                // Signal strength with color coding
                tvSignalLevel.text = "${network.level} dBm (${network.getSignalQuality()}%)"
                val signalColor = when {
                    network.level >= -50 -> ContextCompat.getColor(itemView.context, R.color.signal_excellent)
                    network.level >= -60 -> ContextCompat.getColor(itemView.context, R.color.signal_good)
                    network.level >= -70 -> ContextCompat.getColor(itemView.context, R.color.signal_fair)
                    else -> ContextCompat.getColor(itemView.context, R.color.signal_poor)
                }
                tvSignalLevel.setTextColor(signalColor)
                
                // Frequency
                tvFrequency.text = "${network.frequency} MHz (${network.getFrequencyBand()})"
                
                // Timestamp
                tvTimestamp.text = "Scanned: ${dateFormat.format(Date(network.timestamp))}"
                
                // Check for anomalies
                val anomalies = detectAnomalies(network)
                if (anomalies.isNotEmpty()) {
                    tvAnomaly.visibility = View.VISIBLE
                    tvAnomaly.text = "⚠️ ${anomalies.first()}"
                    
                    // Set anomaly color based on severity
                    val anomalyColor = when {
                        anomalies.any { it.contains("Evil Twin") || it.contains("Open Network") } ->
                            ContextCompat.getColor(itemView.context, R.color.anomaly_critical)
                        anomalies.any { it.contains("Weak Security") || it.contains("Signal Anomaly") } ->
                            ContextCompat.getColor(itemView.context, R.color.anomaly_warning)
                        else -> ContextCompat.getColor(itemView.context, R.color.anomaly_info)
                    }
                    tvAnomaly.setTextColor(anomalyColor)
                } else {
                    tvAnomaly.visibility = View.GONE
                }
            }
        }
        
        private fun detectAnomalies(network: WifiNetwork): List<String> {
            val anomalies = mutableListOf<String>()
            
            // Security anomalies
            when (network.getSecurityType()) {
                "Open" -> anomalies.add("Open Network - Security Risk")
                "WEP" -> anomalies.add("Weak Security - WEP is deprecated")
                "WPA" -> anomalies.add("Weak Security - WPA is outdated")
            }
            
            // Signal anomalies
            if (network.level > -20) {
                anomalies.add("Signal Anomaly - Unusually strong signal")
            }
            
            // Frequency anomalies
            if (network.frequency < 2400 || network.frequency > 5900) {
                anomalies.add("Frequency Anomaly - Unusual frequency")
            }
            
            // Hidden network check
            if (network.ssid.isEmpty()) {
                anomalies.add("Hidden Network - Potential security concern")
            }
            
            return anomalies
        }
    }
}
