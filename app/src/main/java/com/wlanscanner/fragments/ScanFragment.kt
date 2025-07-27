package com.wlanscanner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.wlanscanner.R

class ScanFragment : Fragment() {
    
    private lateinit var scanButton: Button
    private lateinit var statusText: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views - using simple IDs that exist
        scanButton = view.findViewById(R.id.btnStartScan)
        statusText = view.findViewById(R.id.tvScanStatus)
        
        // Setup click listeners
        scanButton.setOnClickListener { 
            Toast.makeText(requireContext(), "Scan functionality - Coming Soon!", Toast.LENGTH_SHORT).show()
            statusText.text = "Scan clicked!"
        }
        
        statusText.text = "WiFi Scanner ready"
    }
}