package com.gismo.safejourney

import android.graphics.Color
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.concurrent.ListenableFuture
import com.esri.arcgisruntime.data.FeatureQueryResult
import com.esri.arcgisruntime.data.QueryParameters
import com.esri.arcgisruntime.data.ServiceFeatureTable
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.location.SimulatedLocationDataSource
import com.esri.arcgisruntime.location.SimulationParameters
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.navigation.RouteTracker
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.tasks.geocode.LocatorTask
import com.esri.arcgisruntime.tasks.networkanalysis.*
import java.io.File
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.properties.Delegates

class MapViewModel(): ViewModel() {

    private val TAG: String = "MapViewModel"

    private val map: ArcGISMap by lazy {
        ArcGISMap(BasemapStyle.ARCGIS_LIGHT_GRAY)
    }

    val locatorTask: LocatorTask by lazy {
        LocatorTask("https://geocode-api.arcgis.com/arcgis/rest/services/World/GeocodeServer")
    }

    var routeTask: RouteTask? = null

    private var _routeFound = MutableLiveData<Boolean>(false)
    val routeFound: LiveData<Boolean> get() = _routeFound

    private var _navStarted = MutableLiveData<Boolean>(false)
    val navStarted: LiveData<Boolean> get() = _navStarted

    val graphicsOverlay: GraphicsOverlay = GraphicsOverlay()

    var routeParameters: RouteParameters? = null
    var routeResult: RouteResult? = null

    // Barrier Layer
    private val barrierList by lazy { mutableListOf<PointBarrier>() }
    private val serviceFeatureTable: ServiceFeatureTable by lazy {
        ServiceFeatureTable("https://services.arcgis.com/hRUr1F8lE8Jq2uJo/arcgis/rest/services/StreetPts/FeatureServer/0")
    }

    // Polygon layer
    private val polygonFeatureTable: ServiceFeatureTable by lazy {
        ServiceFeatureTable("https://services.arcgis.com/hRUr1F8lE8Jq2uJo/arcgis/rest/services/CSO_Mer_Buf20m/FeatureServer/0")
    }



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

    fun findRoute(destination: Point) {

        Log.i(TAG, "Initializing barriers")
        for(i in 1 until barrierList.size) {
            barrierList[i].barrierId = i
            barrierList[i].type = BarrierType.COST_ADJUSTMENT
            barrierList[i].setAddedCost("Minutes", 100.0)
        }

        for(barrier in barrierList) {
            Log.i(TAG, "{${barrier.barrierId}")
            Log.i(TAG, "${barrier.type}")
        }


        Log.i(TAG, "Finding route...")
        routeTask?.loadAsync()
        routeTask?.addDoneLoadingListener {
            if(routeTask?.loadStatus == LoadStatus.LOADED) {
                val info = routeTask?.routeTaskInfo?.costAttributes
                if (info != null) {
                    for(attribute in info) {
                        Log.i(TAG, "${attribute.key}")
                    }
                }
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

                            setPointBarriers(barrierList)


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

                            this.routeResult = routeResult
                            this.routeParameters = routeParameters
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
        Log.i(TAG, "Initializing environment with API Key: $apiKey")
        ArcGISRuntimeEnvironment.setApiKey(apiKey)

        Log.i(TAG, "Loading barrier service")
        val query = QueryParameters()
        query.whereClause = "OBJECTID >= 0"

        val future: ListenableFuture<FeatureQueryResult> = serviceFeatureTable.queryFeaturesAsync(query)
        future.addDoneListener {
            try {
                val result = future.get()
                val resultIterator = result.iterator()
                while(resultIterator.hasNext()) {
                    resultIterator.next().run {
                        if(this.geometry.geometryType.name == "POINT") {
                            barrierList.add(PointBarrier(this.geometry as Point))
                        }
                    }
                }
            } catch(e: Exception) {
                Log.e(TAG, "nards")
                Log.e(TAG, "${e.message}")
            }
        }

        Log.i(TAG, "Loading polygon service")
//        val featureLayer = FeatureLayer(polygonFeatureTable)
//        map.operationalLayers.add(featureLayer)


        val currentTime = Calendar.getInstance()
        var nightTime = Calendar.getInstance()
        nightTime[Calendar.HOUR_OF_DAY] = 18
        nightTime[Calendar.MINUTE] = 0

        if(currentTime >= nightTime)
            map.basemap = Basemap.createDarkGrayCanvasVector()

    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "Destroying MapViewModel")
        mapView?.map = null
    }


}