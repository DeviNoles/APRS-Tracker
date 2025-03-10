package com.kr4byq.aprstracker

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.io.PrintWriter
import java.net.Socket
import java.util.Locale

class AprsService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val callsign = BuildConfig.CALLSIGN
    private val passcode = BuildConfig.PASSCODE
    private val aprsServer = BuildConfig.APRS_SERVER
    private val aprsPort = BuildConfig.APRS_PORT


    override fun onCreate() {
        super.onCreate()
        Log.d("APRS", "Foreground service started!")
        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000) // Update every 30 seconds
            .setMinUpdateDistanceMeters(1f)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    var speed = location.speed
                    var course = location.bearing

                    // if speed or course are 0 assume last values
                    if (speed == 0f) speed = 1f
                    if (course == 0f) course = 360f

                    Log.d("APRS", "GPS Data: lat=$lat, lon=$lon, speed=${speed}m/s (${speed * 1.94384} knots), course=${course}°")

                    sendAprsPacket(lat, lon, speed, course)
                }
            }
        }



        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        try {
            Log.d("APRS", "Requesting location updates...")
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
        .setContentTitle("APRS Tracking Active")
        .setContentText("Sending location updates...")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    private var lastSpeed: Float = 1f
    private var lastCourse: Float = 360f

    private fun formatAprsPacket(lat: Double, lon: Double, speed: Float, course: Float): String {
        val latitude = convertToAprsFormat(lat, true)
        val longitude = convertToAprsFormat(lon, false)

        val speedMph = (speed * 2.23694).toInt().coerceAtLeast(1) // Convert from m/s to MPH
        val courseInt = course.toInt().coerceIn(0, 360)

        return "$callsign>APRS,TCPIP*:!$latitude/$longitude $speedMph MPH $courseInt° Testing app."
    }




    private fun convertToAprsFormat(coord: Double, isLatitude: Boolean): String {
        val absCoord = Math.abs(coord)
        val degrees = absCoord.toInt()
        val minutes = (absCoord - degrees) * 60
        val direction = if (coord >= 0) (if (isLatitude) 'N' else 'E') else (if (isLatitude) 'S' else 'W')

        return if (isLatitude) {
            String.format(Locale.US, "%02d%05.2f%c", degrees, minutes, direction) // 2 digit degrees for lat
        } else {
            String.format(Locale.US, "%03d%05.2f%c", degrees, minutes, direction) // 3 digit degrees for lon
        }
    }


    private var aprsSocket: Socket? = null
    private var aprsOutput: PrintWriter? = null

    private fun connectToAprs() {
        try {
            if (aprsSocket == null || aprsSocket!!.isClosed) {
                Log.d("APRS", "Opening new APRS-IS connection...")
                aprsSocket = Socket(aprsServer, aprsPort)
                aprsOutput = PrintWriter(aprsSocket!!.getOutputStream(), true)

                // login  ONCE
                aprsOutput!!.println("user $callsign pass $passcode vers Android-APRS 1.0 filter m/200")
                Log.d("APRS", "✅ Logged in to APRS-IS")
            }
        } catch (e: Exception) {
            Log.e("APRS", "🚨 Connection error: ${e.message}")
        }
    }
    private fun sendAprsPacket(lat: Double, lon: Double, speed: Float, course: Float) {
        val hardcodedLat = 29.186302
        val hardcodedLon = -82.136217
        val aprsMessage = formatAprsPacket(lat, lon, speed, course) // Using hardcoded coords

        Thread {
            try {
                connectToAprs() // make sure connection active before sending packet
                aprsOutput?.let {
                    it.println(aprsMessage)
                    Log.d("APRS", "✅ APRS Packet Sent: $aprsMessage")
                } ?: Log.e("APRS", "🚨 APRS output stream is null!")
            } catch (e: Exception) {
                Log.e("APRS", "🚨 Error sending APRS Packet: ${e.message}")
            }
        }.start()
    }





}
