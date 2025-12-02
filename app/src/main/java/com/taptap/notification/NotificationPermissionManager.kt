package com.taptap.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to manage notification permission requests for Android 13+
 */
@Singleton
class NotificationPermissionManager @Inject constructor() {

    /**
     * Check if notification permission is granted
     */
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older versions
        }
    }

    /**
     * Check if we should show rationale for notification permission
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
    }

    companion object {
        /**
         * Request notification permission (to be called from a ComponentActivity)
         * Returns a launcher that can be used to request permission
         */
        fun createPermissionLauncher(
            activity: ComponentActivity,
            onResult: (Boolean) -> Unit
        ) = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            android.util.Log.d("NotificationPermission", "Permission granted: $isGranted")
            onResult(isGranted)
        }

        /**
         * Launch permission request
         */
        fun requestPermission(
            launcher: androidx.activity.result.ActivityResultLauncher<String>
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

