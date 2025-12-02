package com.taptap.model

import org.json.JSONObject

/**
 * User profile data model
 * Uses Firebase Auth UID as the primary identifier
 */
data class User(
    var userId: String = "", // Firebase Auth UID
    var createdAt: Long = 0L,
    var lastSeen: String = "",
    var fullName: String = "",
    var phone: String = "",
    var email: String = "",
    var description: String = "",
    var location: String = "",
    var profileImageUrl: String = "",
    var socialLinks: List<SocialLink> = emptyList(),

    // Legacy fields - maintained for backward compatibility
    @Deprecated("Use socialLinks instead")
    var linkedIn: String = "",
    @Deprecated("Use socialLinks instead")
    var github: String = "",
    @Deprecated("Use socialLinks instead")
    var instagram: String = "",
    @Deprecated("Use socialLinks instead")
    var website: String = ""
) {
    fun toJson(): String {
        val json = JSONObject()

        json.put("userId", userId)
        json.put("createdAt", createdAt)
        json.put("lastSeen", lastSeen)
        json.put("fullName", fullName)
        json.put("phone", phone)
        json.put("email", email)
        json.put("description", description)
        json.put("location", location)
        json.put("profileImageUrl", profileImageUrl)
        json.put("socialLinks", SocialLink.listToJsonArray(socialLinks))

        // Legacy fields for backward compatibility
        json.put("linkedIn", linkedIn)
        json.put("github", github)
        json.put("instagram", instagram)
        json.put("website", website)

        return json.toString()
    }

    /**
     * Convert to map for Firestore
     */
    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "userId" to userId,
            "createdAt" to createdAt,
            "lastSeen" to lastSeen,
            "fullName" to fullName,
            "phone" to phone,
            "email" to email,
            "description" to description,
            "location" to location,
            "profileImageUrl" to profileImageUrl,
            "socialLinks" to SocialLink.listToMapList(socialLinks),
            // Legacy fields
            "linkedIn" to linkedIn,
            "github" to github,
            "instagram" to instagram,
            "website" to website,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    companion object {
        fun fromJson(jsonString: String): User {
            val json = JSONObject(jsonString)

            // Parse social links if available
            val socialLinks = try {
                json.optJSONArray("socialLinks")?.let { array ->
                    SocialLink.listFromJsonArray(array)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            return User(
                userId = json.optString("userId", ""),
                createdAt = json.optLong("createdAt", 0L),
                lastSeen = json.optString("lastSeen", ""),
                fullName = json.optString("fullName", ""),
                phone = json.optString("phone", ""),
                email = json.optString("email", ""),
                description = json.optString("description", ""),
                location = json.optString("location", ""),
                profileImageUrl = json.optString("profileImageUrl", ""),
                socialLinks = socialLinks,
                linkedIn = json.optString("linkedIn", ""),
                github = json.optString("github", ""),
                instagram = json.optString("instagram", ""),
                website = json.optString("website", "")
            )
        }

        /**
         * Create User from Firestore document
         */
        fun fromMap(map: Map<String, Any>): User {
            // Parse social links if available
            val socialLinks = try {
                (map["socialLinks"] as? List<*>)?.let { list ->
                    SocialLink.listFromMapList(list)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            return User(
                userId = map["userId"] as? String ?: "",
                createdAt = map["createdAt"] as? Long ?: 0L,
                lastSeen = map["lastSeen"] as? String ?: "",
                fullName = map["fullName"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                email = map["email"] as? String ?: "",
                description = map["description"] as? String ?: "",
                location = map["location"] as? String ?: "",
                profileImageUrl = map["profileImageUrl"] as? String ?: "",
                socialLinks = socialLinks,
                linkedIn = map["linkedIn"] as? String ?: "",
                github = map["github"] as? String ?: "",
                instagram = map["instagram"] as? String ?: "",
                website = map["website"] as? String ?: ""
            )
        }
    }
}

