package com.kr4byq.aprstracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity

import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    val LOCATION_PERMISSION_REQUEST_CODE = 1
    val AUTOCOMPLETE_REQUEST_CODE = 1

    private val locationHistory = mutableListOf<LatLng>()
    private var polyline: Polyline? = null

    private lateinit var mMap: GoogleMap
    private lateinit var roadNameTextView: TextView
    private lateinit var speedBearingTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var lastKnownBearing: Float = 0f
    private var lastKnownSpeed: Float = 0f
    private var isFollowingUser = true
    private var destinationMode = false
    private var searchLocation = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        getOrSetPermissions()

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val startGPSButton = findViewById<Button>(R.id.startGPSButton)
        val stopGPSButton = findViewById<Button>(R.id.stopGPSButton)
        roadNameTextView = findViewById(R.id.roadNameText)
        speedBearingTextView = findViewById(R.id.speedBearingText)
        startButton.setOnClickListener {
            Log.d("TAG", "START BUTTON CLICKED")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val serviceIntent = Intent(this, AprsService::class.java).apply {
                    putExtra("searchLocation", searchLocation)
                    putExtra("destinationMode", destinationMode)

                }
                startForegroundService(serviceIntent)
                Toast.makeText(this, "🚨🚨🚨 APRS STARTED 🚨🚨🚨", Toast.LENGTH_SHORT).show()

            } else {
                Log.e("TAG", "Permissions not granted!")
                Toast.makeText(this, "Location not granted", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            Toast.makeText(this, "✅ APRS STOPPED", Toast.LENGTH_SHORT).show()
            stopService(Intent(this, AprsService::class.java))

        }
        startGPSButton.setOnClickListener {
            Toast.makeText(this, "🚨 GPS STARTED 🚨", Toast.LENGTH_SHORT).show()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val serviceIntent = Intent(this, AwsService::class.java)
                startForegroundService(serviceIntent)
            } else {
                Log.e("TAG", "Permissions not granted!")
                Toast.makeText(this, "Location not granted", Toast.LENGTH_SHORT).show()
            }
        }

        stopGPSButton.setOnClickListener {
            Toast.makeText(this, "✅ GPS STOPPED", Toast.LENGTH_SHORT).show()
            stopService(Intent(this, AwsService::class.java))
        }
        val searchInput = findViewById<EditText>(R.id.searchBox)
        val searchButton = findViewById<Button>(R.id.searchButton)

        searchButton.setOnClickListener {
            searchLocation = searchInput.text.toString().trim()
            Log.d("GPS", searchLocation)
            if (isLatLng(searchLocation)) {
                destinationMode = true
                AlertDialog.Builder(this)
                    .setTitle("Valid location")
                    .setMessage(searchLocation)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            else {
                destinationMode = false
                Toast.makeText(this, "Invalid location", Toast.LENGTH_SHORT).show()
            }
        }
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


        startLocationUpdates()
    }
    private fun drawTrail() { //TODO test this
        polyline?.remove()
        polyline = mMap.addPolyline(
            PolylineOptions()
                .addAll(locationHistory)
                .color(ContextCompat.getColor(this, R.color.black))
                .width(5f)
        )
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val place = Autocomplete.getPlaceFromIntent(data!!)
                val latLng = place.latLng
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                mMap.addMarker(MarkerOptions().position(latLng).title(place.name))
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                val status = Autocomplete.getStatusFromIntent(data!!)
                Log.e("MapsActivity", "Error: ${status.statusMessage}")
            }
        }
    }
    private fun isLatLng(input: String): Boolean {
        // (lat, lon) || lat, lon format
        val regex = """(\()?[-+]?[0-9]*\.?[0-9]+\s*,\s*[-+]?[0-9]*\.?[0-9]+(\))?""".toRegex()
        Log.d("GPS", regex.matches(input).toString())
        return regex.matches(input)
    }
    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) //*update 1 sec
            .setMinUpdateDistanceMeters(5f) //moved>5m updates evvery 1 sec or move 5m< whichever comes first
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    lastKnownBearing = location.bearing
                    lastKnownSpeed = location.speed

                    val userLocation = LatLng(lat, lon)
                    locationHistory.add(userLocation)

                    val cameraPosition = CameraPosition.Builder()
                        .target(userLocation)
                        .zoom(18f)
                        .bearing(lastKnownBearing)
                        .build()
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

                    drawTrail()

                    updateUI()
                    updateRoadInfo(lat, lon)
                }
            }

        }
    }
    private fun getOrSetPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // >=android 14
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "APRS tracking enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied.", Toast.LENGTH_LONG).show()
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
            bearing >= 337.5 || bearing < 22.5 -> "N"
            bearing >= 22.5 && bearing < 67.5 -> "NE"
            bearing >= 67.5 && bearing < 112.5 -> "E"
            bearing >= 112.5 && bearing < 157.5 -> "SE"
            bearing >= 157.5 && bearing < 202.5 -> "S"
            bearing >= 202.5 && bearing < 247.5 -> "SW"
            bearing >= 247.5 && bearing < 292.5 -> "W"
            bearing >= 292.5 && bearing < 337.5 -> "NW"
            else -> "Unknown Direction"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
