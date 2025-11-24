package com.taptap.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taptap.model.User
import com.taptap.repository.UserRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

class UserViewModel(context: Context) : ViewModel() {

    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> = _currentUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var sharedPreferences: SharedPreferences? = null
    private val userRepository = UserRepository()

    init {
        sharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        loadUserFromStorage()
    }

    private fun loadUserFromStorage() {
        // First try to load from local storage (SharedPreferences)
        val savedUserJson = sharedPreferences?.getString("user_data", null)
        if (savedUserJson != null && savedUserJson.isNotEmpty()) {
            try {
                val savedUser = User.fromJson(savedUserJson)
                _currentUser.value = savedUser

                // Then sync with Firestore in the background
                syncWithFirestore(savedUser.userId)
            } catch (e: Exception) {
                createDefaultUser()
            }
        } else {
            createDefaultUser()
        }
    }

    private fun syncWithFirestore(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            userRepository.getUser(userId)
                .onSuccess { firestoreUser ->
                    if (firestoreUser != null) {
                        // Update local data with Firestore data
                        _currentUser.value = firestoreUser
                        saveUserToLocalStorage(firestoreUser)
                    } else {
                        // User doesn't exist in Firestore, upload local user
                        _currentUser.value?.let { localUser ->
                            saveUserToFirestore(localUser)
                        }
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to sync with cloud: ${error.message}"
                }
            _isLoading.value = false
        }
    }

    private fun createDefaultUser() {
        val defaultUser = User(
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
        saveUserToLocalStorage(defaultUser)

        // Save to Firestore as well
        saveUserToFirestore(defaultUser)
    }

    private fun saveUserToLocalStorage(user: User) {
        val editor = sharedPreferences?.edit()
        if (editor != null) {
            editor.putString("user_data", user.toJson())
            editor.apply()
        }
    }

    private fun saveUserToFirestore(user: User) {
        viewModelScope.launch {
            _isLoading.value = true
            userRepository.saveUser(user)
                .onSuccess {
                    // Successfully saved to Firestore
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to save to cloud: ${error.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * Main save function that updates the global user data
     * Saves to both local storage and Firestore
     */
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
            saveUserToLocalStorage(updatedUser)
            saveUserToFirestore(updatedUser)
        }
    }

    /**
     * Update user's last seen status
     */
    fun updateLastSeen(lastSeen: String) {
        val current = _currentUser.value
        if (current != null) {
            val updatedUser = current.copy(lastSeen = lastSeen)
            _currentUser.value = updatedUser
            saveUserToLocalStorage(updatedUser)

            // Update in Firestore
            viewModelScope.launch {
                userRepository.updateLastSeen(current.userId, lastSeen)
            }
        }
    }

    /**
     * Force refresh user data from Firestore
     */
    fun refreshUserFromFirestore() {
        _currentUser.value?.userId?.let { userId ->
            syncWithFirestore(userId)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
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


