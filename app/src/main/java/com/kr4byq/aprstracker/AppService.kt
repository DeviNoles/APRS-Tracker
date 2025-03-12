package com.kr4byq.aprstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AprsService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val callsign = BuildConfig.CALLSIGN
    private val passcode = BuildConfig.PASSCODE
    private val aprsServer = BuildConfig.APRS_SERVER
    private val aprsPort = BuildConfig.APRS_PORT
    private val handler = android.os.Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        Log.d("APRS", aprsPort.toString())

        createNotificationChannel()

        startForeground(1, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setMinUpdateDistanceMeters(1f)

            .setMinUpdateIntervalMillis(30000)
            .setWaitForAccurateLocation(false)
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
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "aws_channel",
                "AWS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Uploads GPS data to AWS"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "aws_channel")
            .setContentTitle("AWS Tracking Active")
            .setContentText("Uploading location updates to AWS RDS...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun requestLocationUpdates() {
        try {
            Log.d("APRS", "Requesting location updates.,")

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

            handler.postDelayed(object : Runnable {
                override fun run() {
                    Log.d("APRS", "forcing location update and APRS packet.")
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val lat = location.latitude
                            val lon = location.longitude
                            val speed = location.speed
                            val course = location.bearing
                            sendAprsPacket(lat, lon, speed, course)
                        }
                    }
                    handler.postDelayed(this, 30000)
                }
            }, 30000)

        } catch (e: SecurityException) {
            Log.e("APRS", "Location permission missing ${e.message}")
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        Log.d("APRS", "🚨 AprsService stopped!🚨")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        aprsOutput?.close()
        aprsSocket?.close()
        aprsOutput = null
        aprsSocket = null
        stopForeground(true)

        // STOP HANDLER LOOP 🚨
        handler.removeCallbacksAndMessages(null)

        stopSelf()

        Log.d("APRS", "cleanup done")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var lastSpeed: Float = 1f
    private var lastCourse: Float = 360f

    private fun formatAprsPacket(lat: Double, lon: Double, speed: Float, course: Float): String {
        val latitude = convertToAprsFormat(lat, true)
        val longitude = convertToAprsFormat(lon, false)

        val timestamp = SimpleDateFormat("HHmmss'Z'", Locale.US).format(Date())

        val speedKnots = (speed * 1.94384).toInt().coerceIn(1, 999)
        val courseInt = course.toInt().coerceIn(0, 360)

        return "$callsign>APRS,TCPIP*:@$timestamp$latitude/$longitude>Sent from my Android"
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
            Log.e("APRS", "Connection error: ${e.message}")
        }
    }
    private fun sendAprsPacket(lat: Double, lon: Double, speed: Float, course: Float) {
        val hardcodedLat = 29.186302

        val hardcodedLon = -82.136217
        val aprsMessage = formatAprsPacket(hardcodedLat, hardcodedLon, speed, course) // Using hardcoded coords

        Thread {
            try {
                connectToAprs() // make sure connection active before sending packet
                aprsOutput?.let {
                    it.println(aprsMessage)

                    Log.d("APRS", "APRS Packet Sent: $aprsMessage")

                } ?: Log.e("APRS", "🚨 APRS output stream is null!")
            } catch (e: Exception) {
                Log.e("APRS", "🚨 Error sending APRS Packet: ${e.message}")
            }
        }.start()
    }





}
