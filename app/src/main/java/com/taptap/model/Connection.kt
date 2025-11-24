package com.taptap.model

/**
 * Represents a connection between two users
 * Stores information about when and where users connected
 */
data class Connection(
    val connectionId: String = "", // Unique ID for this connection
    val userId: String = "", // Current user's Firebase Auth UID
    val connectedUserId: String = "", // Connected user's Firebase Auth UID
    val connectedUserName: String = "",
    val connectedUserEmail: String = "",
    val connectedUserPhone: String = "",
    val connectedUserLinkedIn: String = "",
    val connectedUserDescription: String = "",
    val connectedUserLocation: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val connectionMethod: String = "NFC", // NFC or QR
    val eventName: String = "", // Optional event tag
    val eventLocation: String = "", // Where the connection happened
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val notes: String = "" // User's personal notes about this connection
) {
    /**
     * Convert to map for Firestore
     */
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "connectionId" to connectionId,
            "userId" to userId,
            "connectedUserId" to connectedUserId,
            "connectedUserName" to connectedUserName,
            "connectedUserEmail" to connectedUserEmail,
            "connectedUserPhone" to connectedUserPhone,
            "connectedUserLinkedIn" to connectedUserLinkedIn,
            "connectedUserDescription" to connectedUserDescription,
            "connectedUserLocation" to connectedUserLocation,
            "timestamp" to timestamp,
            "connectionMethod" to connectionMethod,
            "eventName" to eventName,
            "eventLocation" to eventLocation,
            "latitude" to latitude,
            "longitude" to longitude,
            "notes" to notes
        )
    }

    companion object {
        /**
         * Create Connection from Firestore document
         */
        fun fromMap(map: Map<String, Any>): Connection {
            return Connection(
                connectionId = map["connectionId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                connectedUserId = map["connectedUserId"] as? String ?: "",
                connectedUserName = map["connectedUserName"] as? String ?: "",
                connectedUserEmail = map["connectedUserEmail"] as? String ?: "",
                connectedUserPhone = map["connectedUserPhone"] as? String ?: "",
                connectedUserLinkedIn = map["connectedUserLinkedIn"] as? String ?: "",
                connectedUserDescription = map["connectedUserDescription"] as? String ?: "",
                connectedUserLocation = map["connectedUserLocation"] as? String ?: "",
                timestamp = map["timestamp"] as? Long ?: 0L,
                connectionMethod = map["connectionMethod"] as? String ?: "NFC",
                eventName = map["eventName"] as? String ?: "",
                eventLocation = map["eventLocation"] as? String ?: "",
                latitude = map["latitude"] as? Double ?: 0.0,
                longitude = map["longitude"] as? Double ?: 0.0,
                notes = map["notes"] as? String ?: ""
            )
        }
    }

    /**
     * Check if connection is older than 2 months
     */
    fun isOlderThanTwoMonths(): Boolean {
        val twoMonthsInMillis = 60L * 24 * 60 * 60 * 1000 // Approximately 2 months
        return System.currentTimeMillis() - timestamp > twoMonthsInMillis
    }

    /**
     * Get formatted date string
     */
    fun getFormattedDate(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}

