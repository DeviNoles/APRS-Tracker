package com.kr4byq.aprstracker

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AwsService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val handler = Handler()

    private val apiGatewayUrl = BuildConfig.API_GATEWAY_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        Log.d("AWS", "AWS star")

        createNotificationChannel()
        startForeground(2, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(0f)
            .setMinUpdateIntervalMillis(1000)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    val speed = location.speed
                    val course = location.bearing

                    Log.d("AWS", "Location: lat=$lat, lon=$lon, speed=$speed m/s, course=$course°")
                    sendToAws(lat, lon, speed, course)
                }
            }
        }

        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("AWS", "Location missing: ${e.message}")
        }
    }

    private fun sendToAws(lat: Double, lon: Double, speed: Float, course: Float) {
        val json = JSONObject().apply {
            put("latitude", lat)
            put("longitude", lon)
            put("speed", speed)
            put("course", course)
            put("timestamp", System.currentTimeMillis() / 1000)
        }

        val requestBody = RequestBody.create(MediaType.parse("application/json"), json.toString())

        val request = Request.Builder()
            .url(apiGatewayUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AWS", "Failed to send location: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("AWS", "Location sent to AWS")
                } else {
                    Log.e("AWS", "Failed to send: ${response.code()} ${response.message()}")
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        handler.removeCallbacksAndMessages(null)
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

    override fun onBind(intent: Intent?): IBinder? = null
}
