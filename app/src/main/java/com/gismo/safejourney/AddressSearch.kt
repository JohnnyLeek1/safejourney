package com.gismo.safejourney

import android.util.Log
import androidx.appcompat.widget.SearchView
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters
import com.esri.arcgisruntime.tasks.geocode.LocatorTask

class AddressSearch constructor(addressSearchView: SearchView) {

    private val TAG = "AddressSearch"
    private val addressGeocodeParameters = GeocodeParameters()
    private val locatorTask: LocatorTask by lazy {
        LocatorTask("https://geocode-api.arcgis.com/arcgis/rest/services/World/GeocodeServer")
    }

    init {
        
    }

}