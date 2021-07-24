package com.gismo.safejourney

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.view.MapView
import com.gismo.safejourney.databinding.FragmentMapBinding

class MapFragment: Fragment() {

    private val TAG: String = "MapFragment"
    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val binding = FragmentMapBinding.inflate(inflater, container, false)


        binding.lifecycleOwner = viewLifecycleOwner
        viewModel = ViewModelProvider(this).get(MapViewModel::class.java)
        binding.viewModel = viewModel

        mapView = binding.mapView

        // Request location permission if necessary
        mapView.locationDisplay.addDataSourceStatusChangedListener {
            if(!it.isStarted && it.error != null) {
                requestAppPermissions()
            }
        }

        viewModel.mapView = mapView

        binding.recenterButton.setOnClickListener { viewModel.recenterLocation() }

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.map = null
        mapView.dispose()
    }

    private fun requestAppPermissions() {
        Log.i("MapFragment", "Requesting location permissions")
        val requestCode = 2
        val reqPermissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION)

        val checkFineLocation = ContextCompat.checkSelfPermission(this.requireContext(),
            reqPermissions[0]) == PackageManager.PERMISSION_GRANTED

        val checkCoarseLocation = ContextCompat.checkSelfPermission(this.requireContext(),
            reqPermissions[1]) == PackageManager.PERMISSION_GRANTED

        if(!(checkFineLocation && checkCoarseLocation))
            requestPermissions(reqPermissions, requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mapView.locationDisplay.startAsync()
        } else {
            Toast.makeText(this.requireContext(), getString(R.string.location_permissions_denied), Toast.LENGTH_SHORT).show()
        }
    }


}