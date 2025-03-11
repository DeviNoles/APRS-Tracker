package com.kr4byq.aprstracker

import android.annotation.SuppressLint
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var roadNameTextView: TextView
    private lateinit var speedBearingTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var lastKnownBearing: Float = 0f
    private var lastKnownSpeed: Float = 0f
    private var isFollowingUser = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        roadNameTextView = findViewById(R.id.roadNameText)
        speedBearingTextView = findViewById(R.id.speedBearingText)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupLocationUpdates()

    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //  blue dot
        mMap.isMyLocationEnabled = true

        mMap.setOnCameraMoveListener {
            isFollowingUser = false // stop camera when map moved
        }

        startLocationUpdates()
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) //*update 1 sec
            .setMinUpdateDistanceMeters(5f) //moved>5m updates every 1 sec or move 5m< whichever comes first
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    lastKnownBearing = location.bearing
                    lastKnownSpeed = location.speed // m/s

                    val userLocation = LatLng(lat, lon)

                    if (isFollowingUser) { //todo not sure if this was working when i tried
                        val cameraPosition = CameraPosition.Builder()
                            .target(userLocation)
                            .zoom(18f)
                            .bearing(lastKnownBearing)
                            .build()
                        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                    }

                    updateUI()
                    updateRoadInfo(lat, lon)
                }
            }
        }
    }

    private fun updateUI() {
        val speedMph = (lastKnownSpeed * 2.23694).toInt() //convert m/s to mph
        runOnUiThread {
            speedBearingTextView.text = "Speed: ${speedMph} mph | Bearing: ${lastKnownBearing.toInt()}°"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("MapsActivity", "Location permission missing: ${e.message}")
        }
    }

    private fun updateRoadInfo(lat: Double, lon: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lon, 1)
            val roadName = addresses?.firstOrNull()?.thoroughfare ?: "Unknown Road"
            val mileMarker = extractMileMarker(addresses)
            val direction = getCardinalDirection(lastKnownBearing)
            val mileMarkerText = if (mileMarker != null) "Mile $mileMarker" else ""

            Log.d("MapsActivity", "Road: $roadName | $mileMarkerText | Direction: $direction | Speed: ${lastKnownSpeed * 2.23694} mph | Bearing: ${lastKnownBearing}°")

            runOnUiThread {
                roadNameTextView.text = "$roadName $mileMarkerText ($direction)"
            }
        } catch (e: Exception) {
            Log.e("MapsActivity", "Geocoder failed: ${e.message}")
            runOnUiThread {
                roadNameTextView.text = "Unknown Road"
            }
        }
    }

    private fun extractMileMarker(addresses: List<Address>?): String? {
        addresses?.forEach { address ->
            if (address.extras != null) {
                val mileMarker = address.extras?.getString("Milepost")
                if (!mileMarker.isNullOrEmpty()) return mileMarker
            }
        }
        return null
    }

    private fun getCardinalDirection(bearing: Float): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "Northbound"
            bearing >= 22.5 && bearing < 67.5 -> "Northeastbound"
            bearing >= 67.5 && bearing < 112.5 -> "Eastbound"
            bearing >= 112.5 && bearing < 157.5 -> "Southeastbound"
            bearing >= 157.5 && bearing < 202.5 -> "Southbound"
            bearing >= 202.5 && bearing < 247.5 -> "Southwestbound"
            bearing >= 247.5 && bearing < 292.5 -> "Westbound"
            bearing >= 292.5 && bearing < 337.5 -> "Northwestbound"
            else -> "Unknown Direction"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
