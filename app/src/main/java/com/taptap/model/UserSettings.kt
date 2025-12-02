package com.taptap.model

import org.json.JSONObject

/**
 * User settings data model - simplified to essential privacy preferences
 */
data class UserSettings(
    val userId: String = "",
    val isLocationShared: Boolean = true,
    val isPushNotificationsEnabled: Boolean = true
) {
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "userId" to userId,
            "isLocationShared" to isLocationShared,
            "isPushNotificationsEnabled" to isPushNotificationsEnabled,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("userId", userId)
        json.put("isLocationShared", isLocationShared)
        json.put("isPushNotificationsEnabled", isPushNotificationsEnabled)
        return json
    }

    companion object {
        fun fromMap(map: Map<String, Any>): UserSettings {
            return UserSettings(
                userId = map["userId"] as? String ?: "",
                isLocationShared = map["isLocationShared"] as? Boolean ?: true,
                isPushNotificationsEnabled = map["isPushNotificationsEnabled"] as? Boolean ?: true
            )
        }

        fun fromJson(jsonString: String): UserSettings {
            val json = JSONObject(jsonString)
            return UserSettings(
                userId = json.optString("userId", ""),
                isLocationShared = json.optBoolean("isLocationShared", true),
                isPushNotificationsEnabled = json.optBoolean("isPushNotificationsEnabled", true)
            )
        }
    }
}

