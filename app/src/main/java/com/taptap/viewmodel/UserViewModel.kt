package com.taptap.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.taptap.model.TapTapUser
import org.json.JSONObject

class UserViewModel(context: Context) : ViewModel() {

    private val _currentUser = MutableLiveData<TapTapUser>()
    val currentUser: LiveData<TapTapUser> = _currentUser

    private var sharedPreferences: SharedPreferences? = null

    init {
        sharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        loadUserFromStorage()
    }

    private fun loadUserFromStorage() {
        val savedUserJson = sharedPreferences?.getString("user_data", null)
        if (savedUserJson != null && savedUserJson.isNotEmpty()) {
            try {
                val savedUser = TapTapUser.fromJson(savedUserJson)
                _currentUser.value = savedUser
            } catch (e: Exception) {
                createDefaultUser()
            }
        } else {
            createDefaultUser()
        }
    }

    private fun createDefaultUser() {
        val defaultUser = TapTapUser(
            userId = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            lastSeen = "Online",
            fullName = "John Doe",
            phone = "+1234567890",
            email = "john@example.com",
            linkedIn = "linkedin.com/in/johndoe",
            description = "Software Developer",
            location = "New York, USA"
        )
        _currentUser.value = defaultUser
        saveUserToStorage(defaultUser)
    }

    private fun saveUserToStorage(user: TapTapUser) {
        val editor = sharedPreferences?.edit()
        if (editor != null) {
            editor.putString("user_data", user.toJson())
            editor.apply() // better perf
        }
    }

    // main save function that updates the global user data
    fun saveUserProfile(
        fullName: String,
        phone: String,
        email: String,
        linkedIn: String,
        description: String,
        location: String
    ) {
        val current = _currentUser.value
        if (current != null) {
            val updatedUser = current.copy(
                fullName = fullName,
                phone = phone,
                email = email,
                linkedIn = linkedIn,
                description = description,
                location = location
            )
            _currentUser.value = updatedUser
            saveUserToStorage(updatedUser)
        }
    }

    fun getUserProfileJson(): JSONObject {
        val user = _currentUser.value
        if (user == null) {
            return JSONObject()
        }

        val json = JSONObject()
        json.put("app_id", "com.taptap")
        json.put("userId", user.userId)
        json.put("createdAt", user.createdAt)
        json.put("lastSeen", user.lastSeen)
        json.put("fullName", user.fullName)
        json.put("phone", user.phone)
        json.put("email", user.email)
        json.put("linkedIn", user.linkedIn)
        json.put("description", user.description)
        json.put("location", user.location)
        json.put("timestamp", System.currentTimeMillis())

        return json
    }
}
