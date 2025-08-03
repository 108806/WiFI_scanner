package com.wlanscanner

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wlanscanner.data.WifiNetwork

class ScanResultAdapter(
    private val scanResults: MutableList<WifiNetwork>
) : RecyclerView.Adapter<ScanResultAdapter.ScanResultViewHolder>() {

    class ScanResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ssidText: TextView = itemView.findViewById(R.id.ssidText)
        val bssidText: TextView = itemView.findViewById(R.id.bssidText)
        val signalText: TextView = itemView.findViewById(R.id.signalText)
        val frequencyText: TextView = itemView.findViewById(R.id.frequencyText)
        val frequencyChannelText: TextView = itemView.findViewById(R.id.frequencyChannelText)
        val securityText: TextView = itemView.findViewById(R.id.securityText)
        val anomaliesText: TextView = itemView.findViewById(R.id.anomaliesText)
        val vendorText: TextView = itemView.findViewById(R.id.vendorText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.network_item, parent, false)
        return ScanResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        val network = scanResults[position]
        Log.d("ScanResultAdapter", "Binding live scan at position $position: ${network.bssid}")
        
        // Display SSID (or "Hidden Network" if empty)
        holder.ssidText.text = if (network.ssid.isNotEmpty()) {
            network.ssid
        } else {
            "Hidden Network"
        }
        
        // Display BSSID
        holder.bssidText.text = network.bssid
        
        // Display signal strength
        holder.signalText.text = "${network.level} dBm"
        
        // Display frequency band in top line
        val band = if (network.frequency > 5000) "5GHz" else "2.4GHz"
        holder.frequencyText.text = band
        
        // Display detailed frequency and channel in BSSID line
        val channel = network.getChannel()
        holder.frequencyChannelText.text = "${network.frequency}MHz (Ch${channel})"
        
        // Display security type
        holder.securityText.text = network.getSecurityType()
        
        // Display vendor information from stored data
        Log.d("ScanResultAdapter", "Live scan - BSSID: ${network.bssid}, Vendor: ${network.vendor}")
        holder.vendorText.text = network.vendor
        
        // Hide anomalies for live scan (not calculated yet)
        holder.anomaliesText.visibility = View.GONE
    }

    override fun getItemCount(): Int = scanResults.size
    
    fun updateResults(newResults: List<WifiNetwork>) {
        Log.d("ScanResultAdapter", "updateResults called with ${newResults.size} networks")
        Log.d("ScanResultAdapter", "Before clear: adapter has ${scanResults.size} networks")
        scanResults.clear()
        Log.d("ScanResultAdapter", "After clear: adapter has ${scanResults.size} networks")
        val sortedResults = newResults.sortedByDescending { it.level }
        Log.d("ScanResultAdapter", "Sorted results: ${sortedResults.size} networks")
        scanResults.addAll(sortedResults)
        Log.d("ScanResultAdapter", "After addAll: adapter has ${scanResults.size} networks")
        notifyDataSetChanged()
        Log.d("ScanResultAdapter", "Final adapter size: ${scanResults.size}")
    }
}
