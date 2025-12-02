package com.taptap.model

import org.json.JSONObject

/**
 * User settings data model for privacy and preferences
 */
data class UserSettings(
    val userId: String = "",
    val isLocationShared: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false
) {
    // Backward compatibility
    val shareLocationEnabled: Boolean
        get() = isLocationShared

    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "userId" to userId,
            "isLocationShared" to isLocationShared,
            "shareLocationEnabled" to isLocationShared, // Legacy
            "notificationsEnabled" to notificationsEnabled,
            "darkModeEnabled" to darkModeEnabled,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("userId", userId)
        json.put("isLocationShared", isLocationShared)
        json.put("shareLocationEnabled", isLocationShared) // Legacy
        json.put("notificationsEnabled", notificationsEnabled)
        json.put("darkModeEnabled", darkModeEnabled)
        return json
    }

    companion object {
        fun fromMap(map: Map<String, Any>): UserSettings {
            return UserSettings(
                userId = map["userId"] as? String ?: "",
                isLocationShared = (map["isLocationShared"] as? Boolean)
                    ?: (map["shareLocationEnabled"] as? Boolean)
                    ?: true,
                notificationsEnabled = map["notificationsEnabled"] as? Boolean ?: true,
                darkModeEnabled = map["darkModeEnabled"] as? Boolean ?: false
            )
        }

        fun fromJson(jsonString: String): UserSettings {
            val json = JSONObject(jsonString)
            return UserSettings(
                userId = json.optString("userId", ""),
                isLocationShared = json.optBoolean("isLocationShared",
                    json.optBoolean("shareLocationEnabled", true)),
                notificationsEnabled = json.optBoolean("notificationsEnabled", true),
                darkModeEnabled = json.optBoolean("darkModeEnabled", false)
            )
        }
    }
}

