package com.wlanscanner.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wlanscanner.R
import com.wlanscanner.data.WifiNetwork
import java.text.SimpleDateFormat
import java.util.*

class DatabaseNetworkAdapter(
    private val networks: List<WifiNetwork>,
    private val onNetworkClick: (WifiNetwork) -> Unit
) : RecyclerView.Adapter<DatabaseNetworkAdapter.DatabaseNetworkViewHolder>() {
    
    class DatabaseNetworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ssidText: TextView = itemView.findViewById(R.id.tvSSID)
        val bssidText: TextView = itemView.findViewById(R.id.tvBSSID)
        val signalText: TextView = itemView.findViewById(R.id.tvSecurity) // Using security field for signal
        val frequencyText: TextView = itemView.findViewById(R.id.tvLocationInfo) // Using location field for frequency
        val locationText: TextView = itemView.findViewById(R.id.tvLocationInfo)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestamp)
        val vendorText: TextView = itemView.findViewById(R.id.tvVendor)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DatabaseNetworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_database_network, parent, false)
        return DatabaseNetworkViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DatabaseNetworkViewHolder, position: Int) {
        val network = networks[position]
        
        holder.ssidText.text = network.ssid
        holder.bssidText.text = network.bssid
        holder.signalText.text = "${network.level} dBm"
        holder.frequencyText.text = "${network.frequency} MHz"
        holder.locationText.text = "Lat: %.6f, Lon: %.6f".format(network.latitude, network.longitude)
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        holder.timestampText.text = dateFormat.format(Date(network.timestamp))
        
        // Display vendor information
        val vendorLookup = com.wlanscanner.VendorLookup(holder.itemView.context)
        val vendorInfo = vendorLookup.lookupVendor(network.bssid)
        holder.vendorText.text = vendorInfo.name
        
        // Set signal strength color
        val signalColor = when {
            network.level > -50 -> R.color.signal_excellent
            network.level > -60 -> R.color.signal_good
            network.level > -70 -> R.color.signal_fair
            else -> R.color.signal_poor
        }
        holder.signalText.setTextColor(ContextCompat.getColor(holder.itemView.context, signalColor))
        
        holder.itemView.setOnClickListener {
            onNetworkClick(network)
        }
    }
    
    override fun getItemCount(): Int = networks.size
}