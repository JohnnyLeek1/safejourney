package com.gismo.safejourney

import android.util.Log
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import java.io.File
import kotlin.properties.Delegates

class MapViewModel(): ViewModel() {

    private val TAG: String = "MapViewModel"

    val map: ArcGISMap by lazy {
        ArcGISMap(BasemapStyle.ARCGIS_STREETS).apply {
            loadAsync()
        }
    }

    private val graphicsOverlay: GraphicsOverlay = GraphicsOverlay()

    var mapView by Delegates.observable<MapView?>(null) { _, oldValue, newValue ->
        // Remove graphics overlays from old map
        oldValue?.graphicsOverlays?.clear()

        // Assign map and graphics overlays to new map view
        newValue?.map = map
        newValue?.graphicsOverlays?.add(graphicsOverlay)

        Log.i(TAG, "Setting viewpoint")
        newValue?.setViewpoint(Viewpoint(44.3148, -85.6024, 8000000.0))

        newValue?.locationDisplay?.startAsync()
    }

    fun recenterLocation() {
        mapView?.locationDisplay?.autoPanMode = LocationDisplay.AutoPanMode.RECENTER
    }

    init {
        Log.i(TAG, "MapViewModel initialized")

        val apiKey: String = ApiKey.KEY
        Log.i("MapFragment", "Initializing environment with API Key: $apiKey")
        ArcGISRuntimeEnvironment.setApiKey(apiKey)
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "Destroying MapViewModel")
        mapView?.map = null
    }


}