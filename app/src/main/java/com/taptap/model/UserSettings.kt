package com.taptap.model

import org.json.JSONObject

/**
 * User settings data model - simplified to essential privacy preferences
 */
data class UserSettings(
    val userId: String = "",
    val isLocationShared: Boolean = true,
    val isPushNotificationsEnabled: Boolean = true,
    val followUpReminderDays: Int = 30 // Default 30 days for follow-up reminders
) {
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "userId" to userId,
            "isLocationShared" to isLocationShared,
            "isPushNotificationsEnabled" to isPushNotificationsEnabled,
            "followUpReminderDays" to followUpReminderDays,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("userId", userId)
        json.put("isLocationShared", isLocationShared)
        json.put("isPushNotificationsEnabled", isPushNotificationsEnabled)
        json.put("followUpReminderDays", followUpReminderDays)
        return json
    }

    companion object {
        fun fromMap(map: Map<String, Any>): UserSettings {
            return UserSettings(
                userId = map["userId"] as? String ?: "",
                isLocationShared = map["isLocationShared"] as? Boolean ?: true,
                isPushNotificationsEnabled = map["isPushNotificationsEnabled"] as? Boolean ?: true,
                followUpReminderDays = (map["followUpReminderDays"] as? Long)?.toInt() ?: 30
            )
        }

        fun fromJson(jsonString: String): UserSettings {
            val json = JSONObject(jsonString)
            return UserSettings(
                userId = json.optString("userId", ""),
                isLocationShared = json.optBoolean("isLocationShared", true),
                isPushNotificationsEnabled = json.optBoolean("isPushNotificationsEnabled", true),
                followUpReminderDays = json.optInt("followUpReminderDays", 30)
            )
        }
    }
}

