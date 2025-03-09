package com.kr4byq.aprstracker

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class AprsService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        Log.d("APRS", "Foreground service started!")

        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

//update time
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d("APRS", "onLocationResult trigger returned ${locationResult.locations.size} location(s).")

                if (locationResult.locations.isEmpty()) {
                    Log.e("APRS", "No locations!")
                    return
                }

                locationResult.lastLocation?.let { location ->
                    Log.d("APRS", "New GPS coordinates: lat=${location.latitude}, lon=${location.longitude}")
                    sendAprsPacket(location.latitude, location.longitude)
                } ?: Log.e("APRS", "lastLocation is null!")
            }
        }

requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("APRS", "Location permission missing: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotification() = NotificationCompat.Builder(this, "aprs_channel")
        .setContentTitle("APRS Tracking Active!")
        .setContentText("Sending location updates...")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendAprsPacket(lat: Double, lon: Double) {
        Log.d("APRS", "APRS Coords: $lat, $lon")
    }
}
