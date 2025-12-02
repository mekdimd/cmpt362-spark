package com.taptap.model

import org.json.JSONObject

/**
 * User settings data model - simplified to essential privacy preferences
 */
data class UserSettings(
    val userId: String = "",
    val isLocationShared: Boolean = true,
    val isPushNotificationsEnabled: Boolean = true,
    val isConnectionNotificationEnabled: Boolean = true,
    val isFollowUpNotificationEnabled: Boolean = true,
    val followUpReminderValue: Int = 30,
    val followUpReminderUnit: String = "days"
) {
    val followUpReminderDays: Int
        get() = when (followUpReminderUnit) {
            "minutes" -> 1
            "days" -> followUpReminderValue
            "months" -> followUpReminderValue * 30
            else -> followUpReminderValue
        }
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "userId" to userId,
            "isLocationShared" to isLocationShared,
            "isPushNotificationsEnabled" to isPushNotificationsEnabled,
            "isConnectionNotificationEnabled" to isConnectionNotificationEnabled,
            "isFollowUpNotificationEnabled" to isFollowUpNotificationEnabled,
            "followUpReminderValue" to followUpReminderValue,
            "followUpReminderUnit" to followUpReminderUnit,
            "followUpReminderDays" to followUpReminderDays,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("userId", userId)
        json.put("isLocationShared", isLocationShared)
        json.put("isPushNotificationsEnabled", isPushNotificationsEnabled)
        json.put("isConnectionNotificationEnabled", isConnectionNotificationEnabled)
        json.put("isFollowUpNotificationEnabled", isFollowUpNotificationEnabled)
        json.put("followUpReminderValue", followUpReminderValue)
        json.put("followUpReminderUnit", followUpReminderUnit)
        json.put("followUpReminderDays", followUpReminderDays)
        return json
    }

    companion object {
        fun fromMap(map: Map<String, Any>): UserSettings {
            return UserSettings(
                userId = map["userId"] as? String ?: "",
                isLocationShared = map["isLocationShared"] as? Boolean ?: true,
                isPushNotificationsEnabled = map["isPushNotificationsEnabled"] as? Boolean ?: true,
                isConnectionNotificationEnabled = map["isConnectionNotificationEnabled"] as? Boolean ?: true,
                isFollowUpNotificationEnabled = map["isFollowUpNotificationEnabled"] as? Boolean ?: true,
                followUpReminderValue = (map["followUpReminderValue"] as? Long)?.toInt()
                    ?: (map["followUpReminderDays"] as? Long)?.toInt() ?: 30,
                followUpReminderUnit = map["followUpReminderUnit"] as? String ?: "days"
            )
        }

        fun fromJson(jsonString: String): UserSettings {
            val json = JSONObject(jsonString)
            return UserSettings(
                userId = json.optString("userId", ""),
                isLocationShared = json.optBoolean("isLocationShared", true),
                isPushNotificationsEnabled = json.optBoolean("isPushNotificationsEnabled", true),
                isConnectionNotificationEnabled = json.optBoolean("isConnectionNotificationEnabled", true),
                isFollowUpNotificationEnabled = json.optBoolean("isFollowUpNotificationEnabled", true),
                followUpReminderValue = if (json.has("followUpReminderValue")) {
                    json.optInt("followUpReminderValue", 30)
                } else {
                    json.optInt("followUpReminderDays", 30)
                },
                followUpReminderUnit = json.optString("followUpReminderUnit", "days")
            )
        }
    }
}

