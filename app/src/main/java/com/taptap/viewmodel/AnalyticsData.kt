package com.taptap.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.taptap.model.Connection
import com.taptap.repository.ConnectionRepository
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class AnalyticsData(
    val totalConnections: Int = 0,
    val monthlyConnections: Int = 0,
    val weeklyConnections: Int = 0,
    val connectionMethodStats: Map<String, Int> = emptyMap(),
    val locationStats: Map<String, Int> = emptyMap(),
    val monthlyTrend: Map<String, Int> = emptyMap(),
    val timeOfDayStats: Map<String, Int> = emptyMap(),
    val mostActiveDay: String? = null,
    val averageConnectionsPerWeek: Double = 0.0
)

class AnalyticsViewModel : ViewModel() {

    private val connectionRepository = ConnectionRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _connections = MutableLiveData<List<Connection>>(emptyList())
    val connections: LiveData<List<Connection>> = _connections

    private val _analyticsData = MutableLiveData<AnalyticsData>()
    val analyticsData: LiveData<AnalyticsData> = _analyticsData

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    companion object {
        private const val TAG = "AnalyticsViewModel"
    }

    init {
        loadAnalyticsData()
    }

    fun loadAnalyticsData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val currentUserId = auth.currentUser?.uid
                if (currentUserId == null) {
                    _errorMessage.value = "User not logged in"
                    _isLoading.value = false
                    return@launch
                }

                Log.d(TAG, "Loading analytics for user: $currentUserId")

                val result = connectionRepository.getUserConnections(currentUserId)

                if (result.isSuccess) {
                    val connectionList = result.getOrNull() ?: emptyList()
                    Log.d(TAG, "Loaded ${connectionList.size} connections")

                    _connections.value = connectionList
                    calculateAnalytics(connectionList)
                } else {
                    val error = result.exceptionOrNull()
                    _errorMessage.value = "Failed to load connections: ${error?.message}"
                    Log.e(TAG, "Error loading connections", error)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading analytics: ${e.message}"
                Log.e(TAG, "Error loading analytics", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAnalytics() {
        loadAnalyticsData()
    }

    private fun calculateAnalytics(connections: List<Connection>) {
        Log.d(TAG, "Calculating analytics for ${connections.size} connections")

        val now = Instant.now().atZone(ZoneId.systemDefault())
        val startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
        val startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate()

        val monthlyConnections = connections.count { connection ->
            val connectionDate = Instant.ofEpochMilli(connection.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            !connectionDate.isBefore(startOfMonth)
        }

        val weeklyConnections = connections.count { connection ->
            val connectionDate = Instant.ofEpochMilli(connection.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            !connectionDate.isBefore(startOfWeek)
        }

        Log.d(TAG, "Monthly: $monthlyConnections, Weekly: $weeklyConnections")

        val methodStats = connections.groupBy { it.connectionMethod }
            .mapValues { (_, conns) -> conns.size }

        val locationStats = connections
            .mapNotNull { connection ->
                val location = if (connection.eventLocation.isNotEmpty()) {
                    connection.eventLocation
                } else if (connection.connectedUserLocation.isNotEmpty()) {
                    connection.connectedUserLocation
                } else {
                    null
                }
                location
            }
            .groupBy { it }
            .mapValues { (_, locs) -> locs.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .toMap()

        val monthlyTrend = mutableMapOf<String, Int>()
        val monthFormatter = DateTimeFormatter.ofPattern("MMM")

        for (i in 5 downTo 0) {
            val month = now.minusMonths(i.toLong())
            val monthStart = month.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
            val monthEnd = month.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate()

            val monthConnections = connections.count { connection ->
                val connectionDate = Instant.ofEpochMilli(connection.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                !connectionDate.isBefore(monthStart) && !connectionDate.isAfter(monthEnd)
            }

            val monthName = month.format(monthFormatter)
            monthlyTrend[monthName] = monthConnections
            Log.d(TAG, "Month $monthName: $monthConnections connections")
        }

        val timeStats = mutableMapOf<String, Int>()
        val timeSlots = listOf(
            "12AM", "3AM", "6AM", "9AM",
            "12PM", "3PM", "6PM", "9PM"
        )

        timeSlots.forEach { slot ->
            timeStats[slot] = 0
        }

        connections.forEach { connection ->
            val hour = Instant.ofEpochMilli(connection.timestamp)
                .atZone(ZoneId.systemDefault())
                .hour

            val timeSlot = when (hour) {
                in 0..2 -> "12AM"
                in 3..5 -> "3AM"
                in 6..8 -> "6AM"
                in 9..11 -> "9AM"
                in 12..14 -> "12PM"
                in 15..17 -> "3PM"
                in 18..20 -> "6PM"
                else -> "9PM"
            }
            timeStats[timeSlot] = timeStats.getOrDefault(timeSlot, 0) + 1
        }

        val dayStats = connections.groupBy { connection ->
            Instant.ofEpochMilli(connection.timestamp)
                .atZone(ZoneId.systemDefault())
                .dayOfWeek
        }.mapValues { (_, conns) -> conns.size }

        val mostActiveDay = dayStats.maxByOrNull { it.value }?.key?.getDisplayName(
            java.time.format.TextStyle.SHORT,
            java.util.Locale.getDefault()
        )

        val averageConnectionsPerWeek = if (connections.isNotEmpty()) {
            val firstConnection = connections.minByOrNull { it.timestamp }?.timestamp
            val lastConnection = connections.maxByOrNull { it.timestamp }?.timestamp

            if (firstConnection != null && lastConnection != null) {
                val weeksBetween = ChronoUnit.WEEKS.between(
                    Instant.ofEpochMilli(firstConnection).atZone(ZoneId.systemDefault()).toLocalDate(),
                    Instant.ofEpochMilli(lastConnection).atZone(ZoneId.systemDefault()).toLocalDate()
                ).coerceAtLeast(1)

                (connections.size.toDouble() / weeksBetween).let {
                    (it * 100).roundToInt() / 100.0
                }
            } else {
                0.0
            }
        } else {
            0.0
        }

        val data = AnalyticsData(
            totalConnections = connections.size,
            monthlyConnections = monthlyConnections,
            weeklyConnections = weeklyConnections,
            connectionMethodStats = methodStats,
            locationStats = locationStats,
            monthlyTrend = monthlyTrend,
            timeOfDayStats = timeStats,
            mostActiveDay = mostActiveDay,
            averageConnectionsPerWeek = averageConnectionsPerWeek
        )

        Log.d(TAG, "Analytics calculated: $data")
        _analyticsData.value = data
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
