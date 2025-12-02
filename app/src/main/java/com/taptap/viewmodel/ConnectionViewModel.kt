package com.taptap.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taptap.model.Connection
import com.taptap.model.User
import com.taptap.notification.FollowUpScheduler
import com.taptap.notification.NotificationHelper
import com.taptap.repository.ConnectionRepository
import com.taptap.repository.UserRepository
import com.taptap.service.LocationService
import kotlinx.coroutines.launch

/**
 * ViewModel for managing user connections
 * Handles loading, saving, and managing connection data
 */
class ConnectionViewModel : ViewModel() {

    private val connectionRepository = ConnectionRepository()
    private val userRepository = UserRepository()

    private val _connections = MutableLiveData<List<Connection>>()
    val connections: LiveData<List<Connection>> = _connections

    private val _oldConnections = MutableLiveData<List<Connection>>()
    val oldConnections: LiveData<List<Connection>> = _oldConnections

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _connectionCount = MutableLiveData<Int>()
    val connectionCount: LiveData<Int> = _connectionCount

    private lateinit var locationService: LocationService
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var followUpScheduler: FollowUpScheduler

    fun initializeLocationService(context: Context) {
        locationService = LocationService(context)
        notificationHelper = NotificationHelper(context)
        followUpScheduler = FollowUpScheduler(context)
    }

    /**
     * Load all connections for a user and sync with latest profile data
     * @param userId The user's Firebase Auth UID
     */
    fun loadConnections(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            connectionRepository.getUserConnections(userId)
                .onSuccess { connectionList ->
                    _connections.value = connectionList
                    _connectionCount.value = connectionList.size

                    syncConnectionProfiles(connectionList)
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to load connections: ${error.message}"
                    _isLoading.value = false
                }
        }
    }

    /**
     * Sync connection profiles with fresh data from Firestore
     * Updates each connection with the latest user profile data
     */
    private suspend fun syncConnectionProfiles(connectionList: List<Connection>) {
        val updatedConnections = connectionList.map { connection ->
            val userResult = userRepository.getUser(connection.connectedUserId, forceRefresh = true)
            if (userResult.isSuccess && userResult.getOrNull() != null) {
                val user = userResult.getOrNull()!!
                connectionRepository.updateConnectionProfile(
                    connection.connectionId,
                    user.toMap()
                )
                connection.copy(
                    connectedUserName = user.fullName,
                    connectedUserEmail = user.email,
                    connectedUserPhone = user.phone,
                    connectedUserDescription = user.description,
                    connectedUserLocation = user.location,
                    connectedUserSocialLinks = user.socialLinks
                )
            } else {
                connection
            }
        }

        _connections.value = updatedConnections
        _isLoading.value = false
    }

    /**
     * Load old connections (older than 2 months)
     * @param userId The user's Firebase Auth UID
     */
    fun loadOldConnections(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            connectionRepository.getOldConnections(userId)
                .onSuccess { oldConnectionList ->
                    _oldConnections.value = oldConnectionList
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to load old connections: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Enhanced save connection with location capture
     */
    fun saveConnectionWithLocation(
        userId: String,
        connectedUser: User,
        connectionMethod: String = "NFC",
        context: Context
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val location = locationService.getCurrentLocation()
                var latitude = 0.0
                var longitude = 0.0
                var eventLocation = ""

                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    eventLocation =
                        locationService.getLocationName(location.latitude, location.longitude)

                    Log.d(
                        "ConnectionViewModel",
                        "Captured location: $latitude, $longitude - $eventLocation"
                    )
                } else {
                    Log.w("ConnectionViewModel", "Could not capture location")
                }

                val existingConnections = _connections.value ?: emptyList()
                val alreadyConnected = existingConnections.any {
                    it.connectedUserId == connectedUser.userId
                }

                if (alreadyConnected) {
                    _errorMessage.value = "You're already connected with ${connectedUser.fullName}"
                    _isLoading.value = false
                    return@launch
                }

                val connection = Connection(
                    userId = userId,
                    connectedUserId = connectedUser.userId,
                    connectedUserName = connectedUser.fullName,
                    connectedUserEmail = connectedUser.email,
                    connectedUserPhone = connectedUser.phone,
                    connectedUserDescription = connectedUser.description,
                    connectedUserLocation = connectedUser.location,
                    connectedUserSocialLinks = connectedUser.socialLinks,
                    timestamp = System.currentTimeMillis(),
                    connectionMethod = connectionMethod,
                    eventName = "",
                    eventLocation = eventLocation,
                    latitude = latitude,
                    longitude = longitude
                )

                connectionRepository.saveConnection(connection)
                    .onSuccess { connectionId ->
                        createReverseConnection(connectedUser.userId, userId, connectionMethod)

                        viewModelScope.launch {
                            val settingsResult = userRepository.getUserSettings(userId)
                            val settings = settingsResult.getOrNull()

                            if (settings?.isPushNotificationsEnabled == true &&
                                settings.isConnectionNotificationEnabled
                            ) {
                                notificationHelper.showConnectionNotification(
                                    connectionId = connectionId,
                                    userId = connectedUser.userId,
                                    userName = connectedUser.fullName,
                                    userEmail = connectedUser.email,
                                    userPhone = connectedUser.phone
                                )
                            }
                        }

                        val connectionWithId = connection.copy(connectionId = connectionId)
                        scheduleFollowUpReminder(connectionWithId)

                        _successMessage.value = "Connection saved with location!"
                        loadConnections(userId)
                    }
                    .onFailure { error ->
                        _errorMessage.value = "Failed to save connection: ${error.message}"
                    }

            } catch (e: Exception) {
                _errorMessage.value = "Error capturing location: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Save a new connection
     * @param userId Current user's Firebase Auth UID
     * @param connectedUser The user profile that was connected with
     * @param connectionMethod NFC or QR
     * @param eventName Optional event name
     * @param eventLocation Optional event location
     * @param latitude Optional latitude
     * @param longitude Optional longitude
     */
    fun saveConnection(
        userId: String,
        connectedUser: User,
        connectionMethod: String = "NFC",
        eventName: String = "",
        eventLocation: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val existingConnections = _connections.value ?: emptyList()
            val alreadyConnected = existingConnections.any {
                it.connectedUserId == connectedUser.userId
            }

            if (alreadyConnected) {
                _errorMessage.value = "You're already connected with ${connectedUser.fullName}"
                _isLoading.value = false
                return@launch
            }

            val connection = Connection(
                userId = userId,
                connectedUserId = connectedUser.userId,
                connectedUserName = connectedUser.fullName,
                connectedUserEmail = connectedUser.email,
                connectedUserPhone = connectedUser.phone,
                connectedUserDescription = connectedUser.description,
                connectedUserLocation = connectedUser.location,
                connectedUserSocialLinks = connectedUser.socialLinks,
                timestamp = System.currentTimeMillis(),
                connectionMethod = connectionMethod,
                eventName = eventName,
                eventLocation = eventLocation,
                latitude = latitude,
                longitude = longitude
            )

            connectionRepository.saveConnection(connection)
                .onSuccess { connectionId ->
                    createReverseConnection(connectedUser.userId, userId, connectionMethod)

                    viewModelScope.launch {
                        val settingsResult = userRepository.getUserSettings(userId)
                        val settings = settingsResult.getOrNull()

                        if (settings?.isPushNotificationsEnabled == true &&
                            settings.isConnectionNotificationEnabled
                        ) {
                            notificationHelper.showConnectionNotification(
                                connectionId = connectionId,
                                userId = connectedUser.userId,
                                userName = connectedUser.fullName,
                                userEmail = connectedUser.email,
                                userPhone = connectedUser.phone
                            )
                        }
                    }

                    val connectionWithId = connection.copy(connectionId = connectionId)
                    scheduleFollowUpReminder(connectionWithId)

                    _successMessage.value = "Connection saved successfully!"
                    loadConnections(userId)
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to save connection: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Schedule a follow-up reminder for a connection
     * Uses user's settings to determine delay and checks if enabled
     */
    private fun scheduleFollowUpReminder(connection: Connection) {
        viewModelScope.launch {
            try {
                Log.d("ConnectionViewModel", "═══════════════════════════════════════════════")
                Log.d("ConnectionViewModel", "scheduleFollowUpReminder called")
                Log.d("ConnectionViewModel", "   Connection ID: ${connection.connectionId}")
                Log.d("ConnectionViewModel", "   User ID (owner): ${connection.userId}")
                Log.d("ConnectionViewModel", "   Connected User: ${connection.connectedUserName}")
                Log.d("ConnectionViewModel", "   Connected User ID: ${connection.connectedUserId}")

                val settingsResult = userRepository.getUserSettings(connection.userId)
                val settings = settingsResult.getOrNull()

                if (settings?.isPushNotificationsEnabled != true || !settings.isFollowUpNotificationEnabled) {
                    Log.d(
                        "ConnectionViewModel",
                        "   > Follow-up notifications disabled, skipping schedule"
                    )
                    Log.d("ConnectionViewModel", "═══════════════════════════════════════════════")
                    return@launch
                }

                val delayValue = settings.followUpReminderValue
                val delayUnit = settings.followUpReminderUnit

                Log.d("ConnectionViewModel", "   > Scheduling follow-up in $delayValue $delayUnit")
                Log.d("ConnectionViewModel", "═══════════════════════════════════════════════")
                followUpScheduler.scheduleFollowUpReminder(connection, delayValue, delayUnit)
            } catch (e: Exception) {
                Log.e("ConnectionViewModel", "Failed to schedule follow-up reminder", e)
            }
        }
    }

    /**
     * Create reverse connection for bidirectional relationship
     */
    private suspend fun createReverseConnection(
        userId: String,
        connectedUserId: String,
        connectionMethod: String
    ) {
        try {
            userRepository.getUser(connectedUserId).onSuccess { currentUser ->
                if (currentUser != null) {
                    val reverseConnection = Connection(
                        userId = userId,
                        connectedUserId = connectedUserId,
                        connectedUserName = currentUser.fullName,
                        connectedUserEmail = currentUser.email,
                        connectedUserPhone = currentUser.phone,
                        connectedUserDescription = currentUser.description,
                        connectedUserLocation = currentUser.location,
                        connectedUserSocialLinks = currentUser.socialLinks,
                        timestamp = System.currentTimeMillis(),
                        connectionMethod = connectionMethod
                    )

                    connectionRepository.saveConnection(reverseConnection)
                        .onSuccess { reverseConnectionId ->
                            Log.d(
                                "ConnectionViewModel",
                                "SUCCESS: Reverse connection created with ID: $reverseConnectionId"
                            )
                        }
                }
            }
        } catch (e: Exception) {
            Log.e("ConnectionViewModel", "Failed to create reverse connection", e)
        }
    }

    /**
     * Update connection notes
     * @param connectionId The connection ID
     * @param notes The updated notes
     */
    fun updateConnectionNotes(connectionId: String, notes: String) {
        viewModelScope.launch {
            _isLoading.value = true

            connectionRepository.updateConnectionNotes(connectionId, notes)
                .onSuccess {
                    _successMessage.value = "Notes updated successfully"
                    _connections.value = _connections.value?.map { connection ->
                        if (connection.connectionId == connectionId) {
                            connection.copy(notes = notes)
                        } else {
                            connection
                        }
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to update notes: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Update connection event information
     * @param connectionId The connection ID
     * @param eventName The event name
     * @param eventLocation The event location
     */
    fun updateConnectionEvent(
        connectionId: String,
        eventName: String,
        eventLocation: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            connectionRepository.updateConnectionEvent(connectionId, eventName, eventLocation)
                .onSuccess {
                    _successMessage.value = "Event information updated"
                    _connections.value = _connections.value?.map { connection ->
                        if (connection.connectionId == connectionId) {
                            connection.copy(eventName = eventName, eventLocation = eventLocation)
                        } else {
                            connection
                        }
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to update event: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Delete a connection
     * @param connectionId The connection ID
     */
    fun deleteConnection(connectionId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            connectionRepository.deleteConnection(connectionId)
                .onSuccess {
                    _successMessage.value = "Connection deleted"
                    _connections.value =
                        _connections.value?.filter { it.connectionId != connectionId }
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to delete connection: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Clear success and error messages
     */
    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    /**
     * Search connections by name, email, or event
     * @param userId The user's Firebase Auth UID
     * @param query The search query
     */
    fun searchConnections(userId: String, query: String) {
        if (query.isBlank()) {
            loadConnections(userId)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            connectionRepository.searchConnections(userId, query)
                .onSuccess { results ->
                    _connections.value = results
                }
                .onFailure { error ->
                    _errorMessage.value = "Search failed: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Get connection statistics
     * @param userId The user's Firebase Auth UID
     */
    fun getConnectionStats(userId: String) {
        viewModelScope.launch {
            connectionRepository.getConnectionCount(userId)
                .onSuccess { count ->
                    _connectionCount.value = count
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to get stats: ${error.message}"
                }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _successMessage.value = null
    }

    /**
     * Refresh a connection's profile data from Firestore
     * Fetches the latest user profile and updates the cached data
     * @param connection The connection to refresh
     */
    fun refreshConnectionProfile(connection: Connection) {
        viewModelScope.launch {
            userRepository.getUser(connection.connectedUserId, forceRefresh = true)
                .onSuccess { user ->
                    if (user != null) {
                        connectionRepository.updateConnectionProfile(
                            connection.connectionId,
                            user.toMap()
                        ).onSuccess {
                            _connections.value = _connections.value?.map { conn ->
                                if (conn.connectionId == connection.connectionId) {
                                    conn.copy(
                                        connectedUserName = user.fullName,
                                        connectedUserEmail = user.email,
                                        connectedUserPhone = user.phone,
                                        connectedUserDescription = user.description,
                                        connectedUserLocation = user.location,
                                        connectedUserSocialLinks = user.socialLinks
                                    )
                                } else {
                                    conn
                                }
                            }
                        }.onFailure { error ->
                            _errorMessage.value = "Failed to update connection: ${error.message}"
                        }
                    } else {
                        _errorMessage.value = "User profile not found"
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to refresh profile: ${error.message}"
                }
        }
    }

    /**
     * Refresh all connections' profile data from Firestore
     * @param userId The current user's ID
     */
    fun refreshAllConnections(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            connectionRepository.getUserConnections(userId)
                .onSuccess { connectionList ->
                    val refreshedConnections = connectionList.map { connection ->
                        val userResult =
                            userRepository.getUser(connection.connectedUserId, forceRefresh = true)
                        if (userResult.isSuccess && userResult.getOrNull() != null) {
                            val user = userResult.getOrNull()!!
                            connectionRepository.updateConnectionProfile(
                                connection.connectionId,
                                user.toMap()
                            )
                            connection.copy(
                                connectedUserName = user.fullName,
                                connectedUserEmail = user.email,
                                connectedUserPhone = user.phone,
                                connectedUserSocialLinks = user.socialLinks,
                                connectedUserDescription = user.description,
                                connectedUserLocation = user.location
                            )
                        } else {
                            connection
                        }
                    }

                    _connections.value = refreshedConnections
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to refresh connections: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Get connections grouped by month
     */
    fun getConnectionsByMonth(): Map<String, List<Connection>> {
        val connections = _connections.value ?: emptyList()
        return connections.groupBy { connection ->
            val date = java.util.Date(connection.timestamp)
            val format = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            format.format(date)
        }
    }

    /**
     * Get connections grouped by event
     */
    fun getConnectionsByEvent(): Map<String, List<Connection>> {
        val connections = _connections.value ?: emptyList()
        return connections
            .filter { it.eventName.isNotEmpty() }
            .groupBy { it.eventName }
    }
}

