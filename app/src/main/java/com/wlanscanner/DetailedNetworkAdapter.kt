package com.wlanscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wlanscanner.data.NetworkDatabase
import java.text.SimpleDateFormat
import java.util.*

class DetailedNetworkAdapter(
    private val networks: MutableList<NetworkDatabase.NetworkEntry>
) : RecyclerView.Adapter<DetailedNetworkAdapter.DetailedNetworkViewHolder>() {

    class DetailedNetworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ssidText: TextView = itemView.findViewById(R.id.ssidText)
        val bssidText: TextView = itemView.findViewById(R.id.bssidText)
        val signalText: TextView = itemView.findViewById(R.id.signalText)
        val frequencyText: TextView = itemView.findViewById(R.id.frequencyText)
        val securityText: TextView = itemView.findViewById(R.id.securityText)
        val scanCountText: TextView = itemView.findViewById(R.id.scanCountText)
        val lastSeenText: TextView = itemView.findViewById(R.id.lastSeenText)
        val anomaliesText: TextView = itemView.findViewById(R.id.anomaliesText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailedNetworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.network_item_detailed, parent, false)
        return DetailedNetworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailedNetworkViewHolder, position: Int) {
        val network = networks[position]
        
        // Display SSID (or "Hidden Network" if empty)
        holder.ssidText.text = if (network.ssid.isNotEmpty()) {
            network.ssid
        } else {
            "Hidden Network"
        }
        
        // Display BSSID
        holder.bssidText.text = "BSSID: ${network.bssid}"
        
        // Display scan count
        holder.scanCountText.text = "${network.scanCount}x"
        
        // Display latest signal strength
        val latestSignal = network.signalHistory.lastOrNull()
        if (latestSignal != null) {
            holder.signalText.text = "${latestSignal.level} dBm"
            
            // Display frequency with channel
            val channel = getChannelFromFrequency(latestSignal.frequency)
            holder.frequencyText.text = "${latestSignal.frequency} MHz (Ch.$channel)"
        } else {
            holder.signalText.text = "N/A"
            holder.frequencyText.text = "N/A"
        }
        
        // Display security types
        holder.securityText.text = network.securityTypes.joinToString(", ")
        
        // Display last seen time
        val lastSeenTime = System.currentTimeMillis() - network.lastSeen
        holder.lastSeenText.text = formatTimeAgo(lastSeenTime)
        
        // Display anomalies count
        val anomaliesCount = network.anomalies.size
        if (anomaliesCount > 0) {
            holder.anomaliesText.visibility = View.VISIBLE
            holder.anomaliesText.text = "⚠ $anomaliesCount issues detected"
            holder.anomaliesText.setTextColor(
                holder.itemView.context.getColor(android.R.color.holo_orange_light)
            )
        } else {
            holder.anomaliesText.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = networks.size
    
    private fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            frequency < 2484 -> (frequency - 2412) / 5 + 1
            frequency == 2484 -> 14
            frequency in 5000..5999 -> (frequency - 5000) / 5
            else -> 0
        }
    }
    
    private fun formatTimeAgo(millisAgo: Long): String {
        val seconds = millisAgo / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            seconds < 60 -> "${seconds}s ago"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${hours / 24}d ago"
        }
    }
}
