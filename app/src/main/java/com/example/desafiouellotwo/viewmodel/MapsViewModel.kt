package com.example.desafiouellotwo.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Color
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.desafiouellotwo.data.db.MarkersDatabase
import com.example.desafiouellotwo.data.models.MarkerEntity
import com.example.desafiouellotwo.data.repository.MarkerRepository
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import kotlinx.coroutines.launch
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory

class MapsViewModel(application: Application) : AndroidViewModel(application) {
    private val _markers = MutableLiveData<List<MarkerEntity>>()
    val markers: LiveData<List<MarkerEntity>> get() = _markers

    private var markerRepository: MarkerRepository

    private var mutableMarkers: MutableList<Marker> = mutableListOf()

    private val preservedMarkers = mutableListOf<Marker>()

    private val defaultZoom = 15f

    init {
        val database = MarkersDatabase.getInstance(application)
        val markerDao = database.markerDao()
        markerRepository = MarkerRepository(markerDao)
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        fusedLocationClient: FusedLocationProviderClient, locationCallback: LocationCallback
    ) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, null
        )
    }

    fun getAllMarkers() {
        viewModelScope.launch {
            try {
                val result = markerRepository.getAllMarkers()
                _markers.postValue(result)
            } catch (_: Exception) {
            }
        }
    }

    private fun insertMarker(marker: MarkerEntity) {
        viewModelScope.launch {
            try {
                markerRepository.insertMarker(marker)
            } catch (_: Exception) {
            }
        }
    }

    private fun deleteAllMarkers() {
        viewModelScope.launch {
            try {
                markerRepository.deleteAllMarkers()
            } catch (_: Exception) {
            }
        }
    }

    private fun drawPolygon(map: GoogleMap) {
        val markers = mutableMarkers
        if (markers.size >= 3) {

            val newMarkers = markers.filter { marker: Marker ->
                !preservedMarkers.contains(marker)
            }
            preservedMarkers.addAll(newMarkers)

            map.clear()

            val coordinates2 =
                markers.map { Coordinate(it.position.latitude, it.position.longitude) }
                    .toTypedArray()
            val geomFactory = GeometryFactory()
            val convexHull = ConvexHull(coordinates2, geomFactory)
            val hullPoints = convexHull.convexHull.coordinates.map { LatLng(it.x, it.y) }.drop(1)


            val polygonOptions =
                PolygonOptions().addAll(hullPoints).strokeColor(Color.RED).strokeWidth(5f)
                    .fillColor(Color.argb(100, 255, 0, 0)).visible(true)

            map.addPolygon(polygonOptions)
        }

    }

    fun clearMarkers(map: GoogleMap) {
        for (marker in mutableMarkers) {
            marker.remove()
        }
        mutableMarkers.clear()

        for (marker in preservedMarkers) {
            marker.remove()
        }
        preservedMarkers.clear()

        map.clear()

        deleteAllMarkers()
    }

    fun showUserLocation(location: Location, map: GoogleMap) {
        val latLng = LatLng(location.latitude, location.longitude)

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, defaultZoom))
    }

    fun onMapLongClick(latLng: LatLng, map: GoogleMap) {
        val markerOptions = MarkerOptions().position(latLng)
        val mapMarker = map.addMarker(markerOptions)
        if (mapMarker != null) {
            this.mutableMarkers.add(mapMarker)
        }

        val markerEntity = MarkerEntity(
            latitude = latLng.latitude, longitude = latLng.longitude
        )
        insertMarker(markerEntity)

        if (mutableMarkers.size >= 3) {
            drawPolygon(map)
        }
    }

    fun addSavedMarkersToMap(markers: List<MarkerEntity>, map: GoogleMap) {
        for (marker in markers) {
            val latLng = LatLng(marker.latitude, marker.longitude)
            val markerOptions = MarkerOptions().position(latLng)
            val mapMarker = map.addMarker(markerOptions)
            if (mapMarker != null) {
                this.mutableMarkers.add(mapMarker)
            }
            if (mutableMarkers.size >= 3) {
                drawPolygon(map)
            }
        }
    }
}