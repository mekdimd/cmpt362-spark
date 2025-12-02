package com.taptap.service

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationService"
    }

    /**
     * Get current device location with better accuracy
     */
    suspend fun getCurrentLocation(): Location? {
        return try {
            // Check location permissions
            val hasFineLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasCoarseLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
                Log.w(TAG, "Location permission not granted")
                return null
            }

            // Create location request for high accuracy
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000 // 10 seconds timeout
            ).build()

            // Get current location
            val locationResult = fusedLocationClient.getCurrentLocation(
                locationRequest.priority,
                null
            ).await()

            locationResult

        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            // Fallback to last known location
            getLastKnownLocation()
        }
    }

    /**
     * Get last known location as fallback
     */
    private suspend fun getLastKnownLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            null
        }
    }

    /**
     * Get location name from coordinates (reverse geocoding)
     */
    suspend fun getLocationName(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                // Try to get a readable location name
                val locality = address.locality
                val adminArea = address.adminArea
                val country = address.countryName

                when {
                    locality != null && adminArea != null -> "$locality, $adminArea"
                    locality != null -> locality
                    adminArea != null -> adminArea
                    country != null -> country
                    else -> "Unknown Location"
                }
            } else {
                "Unknown Location"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location name", e)
            "Unknown Location"
        }
    }
}
