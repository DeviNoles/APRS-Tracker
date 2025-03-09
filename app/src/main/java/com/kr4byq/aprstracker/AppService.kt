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

    private val callsign = "KR4BYQ"
    private val passcode = ""
    private val aprsServer = "rotate.aprs2.net"
    private val aprsPort = 14580

    override fun onCreate() {
        super.onCreate()
        Log.d("APRS", "Foreground service started!")
        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000).build() // 30 sec update

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("APRS", "New GPS location: lat=${location.latitude}, lon=${location.longitude}")
                    sendAprsPacket(location.latitude, location.longitude)
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
    private fun formatAprsPacket(lat: Double, lon: Double): String {
        val aprsCallsign = "$callsign-9" //mobile needs -9
        val latitude = convertToAprsFormat(lat, true)
        val longitude = convertToAprsFormat(lon, false)

        return "$aprsCallsign>APRS,TCPIP*:!$latitude/$longitude>Android APRS Tracker"
    }

    private fun convertToAprsFormat(coord: Double, isLatitude: Boolean): String {
        val absCoord = Math.abs(coord)
        val degrees = absCoord.toInt()
        val minutes = (absCoord - degrees) * 60
        val direction = if (coord >= 0) (if (isLatitude) 'N' else 'E') else (if (isLatitude) 'S' else 'W')

        return String.format(Locale.US, "%02d%05.2f%c", degrees, minutes, direction)
    }

    private fun sendAprsPacket(lat: Double, lon: Double) {
        val aprsMessage = formatAprsPacket(lat, lon)
        Thread {
            try {
                Log.d("APRS", "Connecting to APRS-IS...")
                val socket = Socket(aprsServer, aprsPort)
                val output = PrintWriter(socket.getOutputStream(), true)

                output.println("user $callsign pass $passcode vers Android-APRS 1.0 filter m/200")
                output.println(aprsMessage)

                Log.d("APRS", "APRS Packet Sent: $aprsMessage")
                output.close()
                socket.close()
            } catch (e: Exception) {
                Log.e("APRS", "Error sending APRS Packet: ${e.message}")
            }
        }.start()
    }
}
