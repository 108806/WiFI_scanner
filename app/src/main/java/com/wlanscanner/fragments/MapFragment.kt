package com.wlanscanner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.wlanscanner.R
import com.wlanscanner.data.NetworkDatabase

class MapFragment : Fragment() {
    
    private lateinit var database: NetworkDatabase
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        database = NetworkDatabase.getInstance(requireContext())
        
        // For now, just show a placeholder message
        // Google Maps integration can be added later with proper API key
        Toast.makeText(requireContext(), "Map functionality - Coming Soon!", Toast.LENGTH_SHORT).show()
    }
}