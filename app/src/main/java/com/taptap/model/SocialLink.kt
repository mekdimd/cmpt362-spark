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
     * Social platform enum with icon resources
     */
    enum class SocialPlatform(val displayName: String, val icon: ImageVector) {
        LINKEDIN("LinkedIn", Icons.Default.Work),
        GITHUB("GitHub", Icons.Default.Code),
        INSTAGRAM("Instagram", Icons.Default.PhotoCamera),
        TWITTER("Twitter", Icons.Default.AlternateEmail),
        FACEBOOK("Facebook", Icons.Default.Group),
        YOUTUBE("YouTube", Icons.Default.PlayArrow),
        TIKTOK("TikTok", Icons.Default.MusicNote),
        WEBSITE("Website", Icons.Default.Language),
        EMAIL("Email", Icons.Default.Email),
        PHONE("Phone", Icons.Default.Phone),
        CUSTOM("Custom", Icons.Default.Link);

        companion object {
            fun fromString(value: String): SocialPlatform {
                return entries.find { it.name == value } ?: CUSTOM
            }
        }
    }

    // Legacy compatibility property
    @Deprecated("Use platform instead")
    val type: SocialPlatform
        get() = platform

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

