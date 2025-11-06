package com.taptap.model

import org.json.JSONObject

data class TapTapUser(
    var userId: Long = 0,
    var createdAt: Long = 0L,
    var lastSeen: String = "",
    var fullName: String = "",
    var phone: String = "",
    var email: String = "",
    var linkedIn: String = "",
    var description: String = "",
    var location: String = ""
) {
    fun toJson(): String {
        val json = JSONObject()

        json.put("userId", userId)
        json.put("createdAt", createdAt)
        json.put("lastSeen", lastSeen)
        json.put("fullName", fullName)
        json.put("phone", phone)
        json.put("email", email)
        json.put("linkedIn", linkedIn)
        json.put("description", description)
        json.put("location", location)

        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): TapTapUser {
            val json = JSONObject(jsonString)
            return TapTapUser(
                userId = json.optLong("userId", 0),
                createdAt = json.optLong("createdAt", 0L),
                lastSeen = json.optString("lastSeen", ""),
                fullName = json.optString("fullName", ""),
                phone = json.optString("phone", ""),
                email = json.optString("email", ""),
                linkedIn = json.optString("linkedIn", ""),
                description = json.optString("description", ""),
                location = json.optString("location", "")
            )
        }
    }
}
