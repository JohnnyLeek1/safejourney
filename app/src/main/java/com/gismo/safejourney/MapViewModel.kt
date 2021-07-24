package com.gismo.safejourney

import android.graphics.Color
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.tasks.geocode.LocatorTask
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask
import com.esri.arcgisruntime.tasks.networkanalysis.Stop
import java.io.File
import java.util.concurrent.ExecutionException
import kotlin.properties.Delegates

class MapViewModel(): ViewModel() {

    private val TAG: String = "MapViewModel"

    val map: ArcGISMap by lazy {
        ArcGISMap(BasemapStyle.ARCGIS_STREETS).apply {
            loadAsync()
        }
    }

    val locatorTask: LocatorTask by lazy {
        LocatorTask("https://geocode-api.arcgis.com/arcgis/rest/services/World/GeocodeServer")
    }

    private var _routeFound = MutableLiveData<Boolean>(false)
    val routeFound: LiveData<Boolean> get() = _routeFound

    private val graphicsOverlay: GraphicsOverlay = GraphicsOverlay()

    var mapView by Delegates.observable<MapView?>(null) { _, oldValue, newValue ->
        // Remove graphics overlays from old map
        oldValue?.graphicsOverlays?.clear()

        // Assign map and graphics overlays to new map view
        newValue?.map = map
        newValue?.graphicsOverlays?.add(graphicsOverlay)

        Log.i(TAG, "Setting viewpoint")
        newValue?.setViewpoint(Viewpoint(38.9072485,-77.0366464, 100000.0))

        newValue?.locationDisplay?.startAsync()
    }

    var routeTask: RouteTask? = null

    fun findRoute(destination: Point) {

        Log.i(TAG, "Finding route...")
        routeTask?.loadAsync()
        routeTask?.addDoneLoadingListener {
            if(routeTask?.loadStatus == LoadStatus.LOADED) {
                val routeParametersFuture = routeTask?.createDefaultParametersAsync()
                Log.i(TAG, "$routeParametersFuture")
                routeParametersFuture?.addDoneListener {
                    routeParametersFuture.get()
                    // Define route parameters
                    val routeParameters = routeParametersFuture?.get().apply {
                        try {
                            setStops(
                                listOf(
                                    Stop(mapView?.locationDisplay?.mapLocation),
                                    Stop(destination)
                                )
                            )

                            isReturnDirections = true
                            isReturnStops = true
                            isReturnRoutes = true


                        } catch (e: Exception) {
                            when (e) {
                                is InterruptedException, is ExecutionException -> {
                                    val error =
                                        "Error getting the default route parameters: ${e.message}"
                                    Log.e(TAG, error)
                                }
                                else -> throw e
                            }
                        }
                    }

                    val routeResultFuture = routeTask?.solveRouteAsync(routeParameters)
                    routeResultFuture?.addDoneListener {
                        try {
                            val routeResult = routeResultFuture?.get()
                            val routeGeometry = routeResult.routes[0].routeGeometry

                            _routeFound.value = true

                            val routeGraphic = Graphic(
                                routeGeometry,
                                SimpleLineSymbol(
                                    SimpleLineSymbol.Style.SOLID,
                                    Color.parseColor("#F0544F"),
                                    5f
                                )
                            )

                            graphicsOverlay.graphics.add(routeGraphic)
                            mapView?.setViewpointAsync(Viewpoint(routeGeometry.extent))
                        } catch (e: Exception) {
                            when (e) {
                                is InterruptedException, is ExecutionException -> {
                                    val error = "Error creating the route result: ${e.message}"
                                    Log.e(TAG, error)
                                }
                                else -> throw e
                            }
                        }
                    }

                }
            } else {
                Log.e(TAG, "Failed to load route task")
                Log.e(TAG, "${routeTask?.loadError?.cause}")
            }
        }

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