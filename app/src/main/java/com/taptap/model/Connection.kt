package com.taptap.model

import com.google.android.gms.maps.model.LatLng

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
    val connectedUserDescription: String = "",
    val connectedUserLocation: String = "",
    val connectedUserSocialLinks: List<SocialLink> = emptyList(),
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
            "connectedUserDescription" to connectedUserDescription,
            "connectedUserLocation" to connectedUserLocation,
            "connectedUserSocialLinks" to SocialLink.listToMapList(connectedUserSocialLinks),
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
            // Parse social links if available
            val socialLinks = try {
                (map["connectedUserSocialLinks"] as? List<*>)?.let { list ->
                    SocialLink.listFromMapList(list)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            return Connection(
                connectionId = map["connectionId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                connectedUserId = map["connectedUserId"] as? String ?: "",
                connectedUserName = map["connectedUserName"] as? String ?: "",
                connectedUserEmail = map["connectedUserEmail"] as? String ?: "",
                connectedUserPhone = map["connectedUserPhone"] as? String ?: "",
                connectedUserDescription = map["connectedUserDescription"] as? String ?: "",
                connectedUserLocation = map["connectedUserLocation"] as? String ?: "",
                connectedUserSocialLinks = socialLinks,
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
     * Get formatted date string
     */
    fun getFormattedDate(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }


    /**
     * Get relative time string (e.g., "Just now", "5 minutes ago", "Yesterday")
     */
    fun getRelativeTimeString(): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes minute${if (minutes != 1L) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours != 1L) "s" else ""} ago"
            days == 1L -> "Yesterday at ${getTimeString()}"
            days < 7 -> "$days days ago"
            else -> getFormattedDate()
        }
    }

    /**
     * Get just the time portion (HH:mm)
     */
    private fun getTimeString(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Check if connection has valid location data
     */
    fun hasValidLocation(): Boolean {
        return latitude != 0.0 &&
                longitude != 0.0 &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180
    }

    /**
     * Get LatLng for maps
     */
    fun getLatLng(): LatLng? {
        return if (hasValidLocation()) {
            LatLng(latitude, longitude)
        } else {
            null
        }
    }
}

