package com.wlanscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wlanscanner.data.NetworkDatabase
import android.util.Log

class NetworkAdapter(
    private val networks: MutableList<NetworkDatabase.NetworkEntry>
) : RecyclerView.Adapter<NetworkAdapter.NetworkViewHolder>() {

    class NetworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ssidText: TextView = itemView.findViewById(R.id.ssidText)
        val bssidText: TextView = itemView.findViewById(R.id.bssidText)
        val signalText: TextView = itemView.findViewById(R.id.signalText)
        val frequencyText: TextView = itemView.findViewById(R.id.frequencyText)
        val securityText: TextView = itemView.findViewById(R.id.securityText)
        val anomaliesText: TextView = itemView.findViewById(R.id.anomaliesText)
        val vendorText: TextView = itemView.findViewById(R.id.vendorText)
        val frequencyChannelText: TextView = itemView.findViewById(R.id.frequencyChannelText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.network_item, parent, false)
        return NetworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: NetworkViewHolder, position: Int) {
        val network = networks[position]
        Log.d("NetworkAdapter", "Binding network at position $position: ${network.bssid}")
        
        // Display SSID (or "Hidden Network" if empty)
        holder.ssidText.text = if (network.ssid.isNotEmpty()) {
            network.ssid
        } else {
            "Hidden Network"
        }
        
        // Display BSSID
        holder.bssidText.text = network.bssid
        
        // Display latest signal strength
        val latestSignal = network.signalHistory.lastOrNull()
        if (latestSignal != null) {
            holder.signalText.text = "${latestSignal.level} dBm"
            
            // Display frequency band
            val band = if (latestSignal.frequency > 5000) "5GHz" else "2.4GHz"
            holder.frequencyText.text = band
            
            // Display frequency and channel
            val channel = when {
                latestSignal.frequency >= 2412 && latestSignal.frequency <= 2484 -> {
                    if (latestSignal.frequency == 2484) 14 else (latestSignal.frequency - 2412) / 5 + 1
                }
                latestSignal.frequency >= 5170 && latestSignal.frequency <= 5825 -> {
                    (latestSignal.frequency - 5000) / 5
                }
                else -> 0
            }
            holder.frequencyChannelText.text = "${latestSignal.frequency}MHz (Ch$channel)"
        } else {
            holder.signalText.text = "N/A"
            holder.frequencyText.text = "N/A"
            holder.frequencyChannelText.text = "Unknown"
        }
        
        // Display security types (with parentheses)
        holder.securityText.text = "(${network.securityType})"
        
        // Display vendor information from stored data
        Log.d("NetworkAdapter", "Database - BSSID: ${network.bssid}, Vendor: ${network.vendor}")
        holder.vendorText.text = network.vendor
        
        // Display last scan time (hour:minute)
        val lastScanTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(network.lastSeen))
        holder.timeText.text = lastScanTime
        
        // Display anomalies count
        val anomaliesCount = network.anomalies.size
        if (anomaliesCount > 0) {
            holder.anomaliesText.visibility = View.VISIBLE
            holder.anomaliesText.text = "$anomaliesCount issues"
            holder.anomaliesText.setTextColor(
                holder.itemView.context.getColor(android.R.color.holo_orange_light)
            )
        } else {
            holder.anomaliesText.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = networks.size
}
