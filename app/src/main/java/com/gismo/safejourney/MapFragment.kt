package com.gismo.safejourney

import android.content.Context
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.UnitSystem
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.location.RouteTrackerLocationDataSource
import com.esri.arcgisruntime.location.SimulatedLocationDataSource
import com.esri.arcgisruntime.location.SimulationParameters
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.navigation.DestinationStatus
import com.esri.arcgisruntime.navigation.RouteTracker
import com.esri.arcgisruntime.navigation.TrackingStatus
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask
import com.gismo.safejourney.databinding.FragmentMapBinding
import java.util.*
import java.util.concurrent.ExecutionException

class MapFragment: Fragment() {

    private val TAG: String = "MapFragment"
    private lateinit var mapView: MapView
    private lateinit var viewModel: MapViewModel

    private lateinit var startButton: Button
    private lateinit var searchContainer: ConstraintLayout
    private lateinit var searchBar: SearchView
    private lateinit var mapContext: Context

    private val addressGeocodeParameters = GeocodeParameters()

    // Navigation bar
    private lateinit var navigationBar: ConstraintLayout
    private lateinit var timeRemainingValue: TextView
    private lateinit var distanceRemainingValue: TextView

    // Direction panel
    private lateinit var directionsPanel: ConstraintLayout
    private lateinit var roadName: TextView
    private lateinit var roadDistance: TextView
    private lateinit var roadIcon: ImageView

    private lateinit var textToSpeech: TextToSpeech
    private var lastVoiceGuidance: String = ""


    var simulateRoute = false


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
        viewModel.routeTask = RouteTask(this.requireContext(), getString(R.string.world_route_url))


        binding.recenterButton.setOnClickListener { viewModel.recenterLocation() }

        viewModel.routeFound.observe(viewLifecycleOwner) {
            if(it) binding.startButton.visibility = View.VISIBLE
            else binding.startButton.visibility = View.GONE
        }

        startButton = binding.startButton
        binding.startButton.setOnClickListener { startNavigation() }

        // Set context so it can be accessed in geocoder
        mapContext = this.requireContext()
        searchContainer = binding.searchContainer
        searchBar = binding.searchBar

        setupAddressSearchBar()

        // Navigation bar
        navigationBar = binding.navigationBar
        timeRemainingValue = binding.timeRemainingValue
        distanceRemainingValue = binding.distanceRemainingValue

        // Direction Panel
        directionsPanel = binding.directionsPanel
        roadName = binding.roadName
        roadDistance = binding.roadDistance
        roadIcon = binding.roadIcon

        textToSpeech = TextToSpeech(this.requireContext()) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.US
            } else {
                Log.e(TAG, "Failed to initialize text to speech")
            }
        }


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


    private fun setupAddressSearchBar() {
        addressGeocodeParameters.apply {
            resultAttributeNames.addAll(listOf("PlaceName", "Place_addr"))
            maxResults = 1

            searchBar.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(address: String): Boolean {
                    geocodeAddress(address)
                    searchBar.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    if(newText.isNotEmpty()) {
                        val suggestionsFuture = viewModel.locatorTask.suggestAsync(newText)
                        suggestionsFuture.addDoneListener {
                            try {
                                val suggestResults = suggestionsFuture.get()

                                val address = "address"
                                val columnNames = arrayOf(BaseColumns._ID, address)
                                val suggestionsCursor = MatrixCursor(columnNames)

                                for ((key, result) in suggestResults.withIndex()) {
                                    suggestionsCursor.addRow(arrayOf<Any>(key, result.label))
                                }

                                val cols = arrayOf(address)
                                val to = intArrayOf(R.id.suggestion_address)

                                val suggestionAdapter = SimpleCursorAdapter(mapContext, R.layout.suggestion, suggestionsCursor, cols, to, 0)

                                searchBar.suggestionsAdapter = suggestionAdapter

                                searchBar.setOnSuggestionListener(object: SearchView.OnSuggestionListener {
                                    override fun onSuggestionSelect(position: Int): Boolean {
                                        return false
                                    }

                                    override fun onSuggestionClick(position: Int): Boolean {
                                        (suggestionAdapter.getItem(position) as? MatrixCursor)?.let { selectedRow ->
                                            val selectedCursorIndex = selectedRow.getColumnIndex(address)
                                            val selectedAddress = selectedRow.getString(selectedCursorIndex)

                                            searchBar.setQuery(selectedAddress, true)
                                        }
                                        return true
                                    }
                                })
                            } catch(e: Exception) {
                                Log.e(TAG, "Geocode suggestion error ${e.message}")
                            }
                        }
                    }
                    return true
                }
            })
        }
    }

    private fun geocodeAddress(address: String) {
        viewModel.locatorTask.addDoneLoadingListener {
            if(viewModel.locatorTask.loadStatus == LoadStatus.LOADED) {
                val geocodeResultFeature = viewModel.locatorTask.geocodeAsync(address, addressGeocodeParameters)
                geocodeResultFeature.addDoneListener {
                    try {
                        val geocodeResults = geocodeResultFeature.get()
                        if(geocodeResults.isNotEmpty()) {
                            Log.i(TAG, "Found result")
                            viewModel.findRoute(geocodeResults[0].routeLocation)
                        }
                    } catch(e: Exception) {
                        when (e) {
                            is ExecutionException, is InterruptedException -> {
                                Log.e(TAG, "Geocode error: ${e.message}")
                            }
                            else -> throw e
                        }
                    }
                }
            } else {
                viewModel.locatorTask.retryLoadAsync()
            }
            viewModel.locatorTask.loadAsync()
        }
    }

    private fun startNavigation() {
        viewModel.graphicsOverlay.graphics.clear()

        startButton.visibility = View.GONE
        searchContainer.visibility = View.GONE

        navigationBar.visibility = View.VISIBLE
        directionsPanel.visibility = View.VISIBLE

        val routeGeometry = viewModel.routeResult?.routes?.get(0)?.routeGeometry

        speakVoiceGuidance("Starting route")

        val routeAheadGraphic = Graphic(
            routeGeometry,
            SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.parseColor("#2462A0"), 5f)
        )

        val routeTraveledGraphic = Graphic(
            routeGeometry,
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.parseColor("#F0544F"), 5f)
        )

        viewModel.graphicsOverlay.graphics.addAll(listOf(routeAheadGraphic, routeTraveledGraphic))

        // Simulation parameters
        val simulationParameters = SimulationParameters(Calendar.getInstance(), 35.0, 5.0, 5.0)
        val simulatedLocationDataSource = SimulatedLocationDataSource().apply {
            setLocations(routeGeometry, simulationParameters)
        }

        val routeTracker = RouteTracker(this.requireContext(), viewModel.routeResult, 0, true).apply {
            enableReroutingAsync(viewModel.routeTask, viewModel.routeParameters, RouteTracker.ReroutingStrategy.TO_NEXT_WAYPOINT, true)
            voiceGuidanceUnitSystem = UnitSystem.IMPERIAL
        }

        val routeTrackerLocationDataSource = RouteTrackerLocationDataSource(routeTracker, simulatedLocationDataSource)
        mapView.locationDisplay.apply {
            locationDataSource = routeTrackerLocationDataSource
            autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
        }

        mapView.locationDisplay.addLocationChangedListener {
            val trackingStatus = routeTracker.trackingStatus

            routeAheadGraphic.geometry = trackingStatus.routeProgress.remainingGeometry
            routeTraveledGraphic.geometry = trackingStatus.routeProgress.traversedGeometry

            // Get remaining distance info
            val remainingDistance: TrackingStatus.Distance = trackingStatus.destinationProgress.remainingDistance

            timeRemainingValue.text = DateUtils.formatElapsedTime((trackingStatus.destinationProgress.remainingTime * 60).toLong())
            distanceRemainingValue.text = "${remainingDistance.displayText} ${remainingDistance.displayTextUnits.abbreviation}"
            roadDistance.text = "${trackingStatus.maneuverProgress.remainingDistance.displayText} ${trackingStatus.maneuverProgress.remainingDistance.displayTextUnits.abbreviation}"

            routeTracker.addNewVoiceGuidanceListener {
                roadName.text = parseRoadName(it.voiceGuidance.text)
                roadIcon.setImageResource(parseIconDirection(it.voiceGuidance.text))

                speakVoiceGuidance(it.voiceGuidance.text)
            }

            if (trackingStatus.destinationStatus == DestinationStatus.REACHED) {
                if(routeTracker.trackingStatus.remainingDestinationCount > 1) {
                    routeTracker.switchToNextDestinationAsync()
                } else {
                    simulatedLocationDataSource.stop()
                    routeTrackerLocationDataSource.stop()
                }
            }
        }

        mapView.locationDisplay.startAsync()

    }

    /**
     * Human readability processing methods
     */


    /**
     * Parse road name from string
     */
    private fun parseRoadName(direction: String): String {
        Log.i(TAG, direction)
        // Simple, "Turn Left", "Turn Right"
        if(direction == "Turn left") return direction
        else if(direction == "Turn right") return direction

        // For directions like: "Turn right on East Street Northwest" or
        // "Turn left through the Park on East Street Northwest"
        else if("on" in direction && "destination is" !in direction && "on your" !in direction) {
            var directions = direction.split("on ")
            return directions[directions.size - 1]
        }
        // For "make a u-turn directions"
        else if("Make a" in direction) {
            var directions = direction.split(',')
            return directions[0]
        }

        // For "you have arrived"
        else if("You have arrived" in direction) {
            var directions = direction.split(' ')
            return directions[0] + ' ' + directions[1] + ' ' + directions[2]
        }

        return "Calculating..."
    }

    /**
     * Determines which icon to display based off direction string
     */
    private fun parseIconDirection(direction: String): Int {
        if("arrived" in direction) {
            return R.drawable.ic_finish_location
        }

        else if("U turn" in direction) {
            return R.drawable.ic_down_arrow
        }

        else if("left" in direction) {
            return R.drawable.ic_left_arrow
        }

        else if("right" in direction) {
            return R.drawable.ic_right_arrow
        }

        return R.drawable.ic_up_arrow
    }

    /**
     * Speak a string from Text to Speech service
     */
    private fun speakVoiceGuidance(text: String) {
        if(!textToSpeech.isSpeaking && text != lastVoiceGuidance) {
            // Prevent repeat instructions
            lastVoiceGuidance = text
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            else
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }


}