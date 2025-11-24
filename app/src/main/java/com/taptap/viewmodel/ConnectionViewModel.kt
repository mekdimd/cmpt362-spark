package com.taptap.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taptap.model.Connection
import com.taptap.model.User
import com.taptap.repository.ConnectionRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for managing user connections
 * Handles loading, saving, and managing connection data
 */
class ConnectionViewModel : ViewModel() {

    private val connectionRepository = ConnectionRepository()

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

    /**
     * Load all connections for a user
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
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to load connections: ${error.message}"
                }

            _isLoading.value = false
        }
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

            val connection = Connection(
                userId = userId,
                connectedUserId = connectedUser.userId,
                connectedUserName = connectedUser.fullName,
                connectedUserEmail = connectedUser.email,
                connectedUserPhone = connectedUser.phone,
                connectedUserLinkedIn = connectedUser.linkedIn,
                connectedUserDescription = connectedUser.description,
                connectedUserLocation = connectedUser.location,
                timestamp = System.currentTimeMillis(),
                connectionMethod = connectionMethod,
                eventName = eventName,
                eventLocation = eventLocation,
                latitude = latitude,
                longitude = longitude
            )

            connectionRepository.saveConnection(connection)
                .onSuccess { connectionId ->
                    _successMessage.value = "Connection saved successfully!"
                    // Reload connections
                    loadConnections(userId)
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to save connection: ${error.message}"
                }

            _isLoading.value = false
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
                    // Update local list
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
                    // Update local list
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
                    // Remove from local list
                    _connections.value = _connections.value?.filter { it.connectionId != connectionId }
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

