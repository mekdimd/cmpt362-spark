package com.taptap.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

/**
 * Social link data model with support for predefined and custom links
 */
data class SocialLink(
    val id: String = java.util.UUID.randomUUID().toString(),
    val platform: SocialPlatform = SocialPlatform.CUSTOM,
    val url: String = "",
    val label: String = "",
    val isPinned: Boolean = false,
    val order: Int = 0
) {
    /**
     * Social platform enum with icon resources and URL generation
     */
    enum class SocialPlatform(
        val displayName: String,
        val icon: ImageVector,
        val urlPrefix: String,
        val placeholder: String
    ) {
        LINKEDIN("LinkedIn", Icons.Default.Work, "https://linkedin.com/in/", "username"),
        GITHUB("GitHub", Icons.Default.Code, "https://github.com/", "username"),
        INSTAGRAM("Instagram", Icons.Default.PhotoCamera, "https://instagram.com/", "username"),
        TWITTER("Twitter", Icons.Default.AlternateEmail, "https://twitter.com/", "username"),
        FACEBOOK("Facebook", Icons.Default.Group, "https://facebook.com/", "username"),
        YOUTUBE("YouTube", Icons.Default.PlayArrow, "https://youtube.com/@", "channel"),
        TIKTOK("TikTok", Icons.Default.MusicNote, "https://tiktok.com/@", "username"),
        WEBSITE("Website", Icons.Default.Language, "https://", "example.com"),
        EMAIL("Email", Icons.Default.Email, "mailto:", "email@example.com"),
        PHONE("Phone", Icons.Default.Phone, "tel:", "1234567890"),
        CUSTOM("Custom", Icons.Default.Link, "", "URL");

        companion object {
            fun fromString(value: String): SocialPlatform {
                return entries.find { it.name == value } ?: CUSTOM
            }

            /**
             * Generate full URL from handle/username
             */
            fun generateUrl(platform: SocialPlatform, input: String): String {
                // If input already starts with http/https, use it as-is
                if (input.startsWith("http://") || input.startsWith("https://")) {
                    return input
                }

                // For email and phone, check if prefix is already there
                if (platform == EMAIL && input.startsWith("mailto:")) {
                    return input
                }
                if (platform == PHONE && input.startsWith("tel:")) {
                    return input
                }

                // Clean up handle (remove @ if present for social platforms)
                val cleanHandle = when (platform) {
                    INSTAGRAM, TWITTER, TIKTOK, YOUTUBE -> input.removePrefix("@")
                    else -> input
                }

                return platform.urlPrefix + cleanHandle
            }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("platform", platform.name)
        json.put("label", label)
        json.put("url", url)
        json.put("isPinned", isPinned)
        json.put("order", order)
        return json
    }

    fun toMap(): Map<String, Any> {
        return hashMapOf(
            "id" to id,
            "platform" to platform.name,
            "label" to label,
            "url" to url,
            "isPinned" to isPinned,
            "order" to order
        )
    }

    companion object {
        fun fromJson(json: JSONObject): SocialLink {
            return SocialLink(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                platform = SocialPlatform.fromString(json.optString("platform", "CUSTOM")),
                label = json.optString("label", ""),
                url = json.optString("url", ""),
                isPinned = json.optBoolean("isPinned", false),
                order = json.optInt("order", 0)
            )
        }

        fun fromMap(map: Map<String, Any>): SocialLink {
            return SocialLink(
                id = map["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                platform = SocialPlatform.fromString(map["platform"] as? String ?: "CUSTOM"),
                label = map["label"] as? String ?: "",
                url = map["url"] as? String ?: "",
                isPinned = map["isPinned"] as? Boolean ?: false,
                order = (map["order"] as? Long)?.toInt() ?: 0
            )
        }

        fun listToJsonArray(links: List<SocialLink>): JSONArray {
            val array = JSONArray()
            links.forEach { array.put(it.toJson()) }
            return array
        }

        fun listFromJsonArray(jsonArray: JSONArray): List<SocialLink> {
            val links = mutableListOf<SocialLink>()
            for (i in 0 until jsonArray.length()) {
                links.add(fromJson(jsonArray.getJSONObject(i)))
            }
            return links
        }

        fun listToMapList(links: List<SocialLink>): List<Map<String, Any>> {
            return links.map { it.toMap() }
        }

        fun listFromMapList(mapList: List<*>): List<SocialLink> {
            return mapList.mapNotNull {
                (it as? Map<*, *>)?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    fromMap(map as Map<String, Any>)
                }
            }
        }
    }
}

