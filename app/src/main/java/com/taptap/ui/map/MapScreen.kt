package com.taptap.ui.map

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.taptap.service.LocationService
import com.taptap.viewmodel.ConnectionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    connectionViewModel: ConnectionViewModel
) {
    val context = LocalContext.current
    val connections by connectionViewModel.connections.observeAsState(emptyList())
    val coroutineScope = rememberCoroutineScope()

    val locationService = remember { LocationService(context) }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(49.2827, -123.1207), 10f)
    }

    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            isLoadingLocation = true
            locationError = null
            try {
                userLocation = getCurrentUserLocation(locationService)
            } catch (e: Exception) {
                locationError = "Failed to get location: ${e.message}"
            } finally {
                isLoadingLocation = false
            }
        }
    }

    LaunchedEffect(userLocation, connections) {
        safeCenterMapOnLocations(userLocation, connections, cameraPositionState)
    }

    val connectionsWithLocation = connections.filter {
        it.hasValidLocation()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = locationPermissions.allPermissionsGranted,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                zoomControlsEnabled = true,
                compassEnabled = true
            )
        ) {
            connectionsWithLocation.forEach { connection ->
                val connectionLatLng = LatLng(connection.latitude, connection.longitude)

                Marker(
                    state = MarkerState(position = connectionLatLng),
                    title = connection.connectedUserName,
                    snippet = "Connected: ${connection.getFormattedDate()} at ${connection.eventLocation.takeIf { it.isNotEmpty() } ?: "Unknown Location"}",
                )
            }
        }

        if (isLoadingLocation) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        locationError?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (connectionsWithLocation.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "${connectionsWithLocation.size} connection locations",
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Column {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoadingLocation = true
                            locationError = null
                            try {
                                userLocation = getCurrentUserLocation(locationService)
                                userLocation?.let { location ->
                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 15f)
                                }
                            } catch (e: Exception) {
                                locationError = "Failed to get location: ${e.message}"
                            } finally {
                                isLoadingLocation = false
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.MyLocation, "Center on my location")
                }

                FloatingActionButton(
                    onClick = {
                    }
                ) {
                    Icon(Icons.Default.Refresh, "Refresh connections")
                }
            }
        }
    }
}

/**
 * Get current user location with error handling
 */
private suspend fun getCurrentUserLocation(locationService: LocationService): LatLng? {
    return try {
        val location = locationService.getCurrentLocation()
        location?.let { LatLng(it.latitude, it.longitude) }
    } catch (e: Exception) {
        null
    }
}

/**
 * Safe version that won't crash with empty bounds
 */
private suspend fun safeCenterMapOnLocations(
    userLocation: LatLng?,
    connections: List<com.taptap.model.Connection>,
    cameraPositionState: CameraPositionState
) {
    val connectionsWithLocation = connections.filter { it.hasValidLocation() }

    val targetLocation = when {
        userLocation != null -> userLocation
        connectionsWithLocation.isNotEmpty() -> {
            LatLng(connectionsWithLocation.first().latitude, connectionsWithLocation.first().longitude)
        }
        else -> {
            LatLng(49.2827, -123.1207)
        }
    }

    val zoom = when {
        connectionsWithLocation.isEmpty() -> 15f 
        connectionsWithLocation.size == 1 -> 12f 
        else -> 10f 
    }

    cameraPositionState.position = CameraPosition.fromLatLngZoom(targetLocation, zoom)
}
