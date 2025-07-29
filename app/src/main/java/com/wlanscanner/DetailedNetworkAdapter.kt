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
    private val networks: MutableList<NetworkDatabase.NetworkEntry>,
    private val onNetworkClick: (NetworkDatabase.NetworkEntry) -> Unit = {}
) : RecyclerView.Adapter<DetailedNetworkAdapter.DetailedNetworkViewHolder>() {

    class DetailedNetworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ssidText: TextView = itemView.findViewById(R.id.ssidText)
        val scanCountText: TextView = itemView.findViewById(R.id.scanCountText)
        val signalText: TextView = itemView.findViewById(R.id.signalText)
        val technicalDetailsText: TextView = itemView.findViewById(R.id.technicalDetailsText)
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
        
        // Display scan count
        holder.scanCountText.text = "${network.scanCount}x"
        
        // Display latest signal strength
        val latestSignal = network.signalHistory.lastOrNull()
        if (latestSignal != null) {
            holder.signalText.text = "${latestSignal.level} dBm"
            
            // Create compressed technical details line
            val tempNetwork = com.wlanscanner.data.WifiNetwork(
                ssid = network.ssid,
                bssid = network.bssid,
                capabilities = "",
                frequency = latestSignal.frequency,
                level = latestSignal.level,
                timestamp = latestSignal.timestamp,
                latitude = network.locations.lastOrNull()?.latitude ?: 0.0,
                longitude = network.locations.lastOrNull()?.longitude ?: 0.0,
                address = network.address ?: ""
            )
            val channel = tempNetwork.getChannel()
            val security = network.securityTypes.joinToString(",")
            val lastSeenTime = System.currentTimeMillis() - network.lastSeen
            val timeAgo = formatTimeAgo(lastSeenTime)
            
            // Format: BSSID | frequency channel | security | time ago
            holder.technicalDetailsText.text = "${network.bssid} | ${latestSignal.frequency}MHz Ch${channel} | ${security} | ${timeAgo}"
        } else {
            holder.signalText.text = "N/A"
            holder.technicalDetailsText.text = "${network.bssid} | No signal data"
        }
        
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
        
        // Set click listener to show detailed info
        holder.itemView.setOnClickListener {
            onNetworkClick(network)
        }
    }

    override fun getItemCount(): Int = networks.size
    
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
