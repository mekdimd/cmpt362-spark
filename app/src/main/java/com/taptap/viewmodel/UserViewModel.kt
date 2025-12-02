package com.taptap.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.taptap.model.SocialLink
import com.taptap.model.User
import com.taptap.model.UserSettings
import com.taptap.repository.UserRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * UI State for user data
 */
data class UserUiState(
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val settings: UserSettings = UserSettings(),
    val socialLinks: List<SocialLink> = emptyList(),
    val error: String? = null
)

class UserViewModel(context: Context) : ViewModel() {

    private val _uiState = MutableLiveData<UserUiState>()
    val uiState: LiveData<UserUiState> = _uiState

    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> = _currentUser

    private val _userSettings = MutableLiveData<UserSettings>()
    val userSettings: LiveData<UserSettings> = _userSettings

    private val _socialLinks = MutableLiveData<List<SocialLink>>()
    val socialLinks: LiveData<List<SocialLink>> = _socialLinks

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var sharedPreferences: SharedPreferences? = null
    private val userRepository = UserRepository()
    private val auth = FirebaseAuth.getInstance()

    init {
        sharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        loadUserFromStorage()
        loadUserSettings()
        _uiState.value = UserUiState()
    }

    private fun updateUiState() {
        _uiState.value = UserUiState(
            isLoading = _isLoading.value ?: false,
            currentUser = _currentUser.value,
            settings = _userSettings.value ?: UserSettings(),
            socialLinks = _currentUser.value?.socialLinks ?: emptyList(),
            error = _errorMessage.value
        )
        _socialLinks.value = _currentUser.value?.socialLinks ?: emptyList()
    }

    private fun loadUserSettings() {
        val settingsJson = sharedPreferences?.getString("user_settings", null)
        if (settingsJson != null) {
            try {
                _userSettings.value = UserSettings.fromJson(settingsJson)
            } catch (e: Exception) {
                // Use default settings
                _userSettings.value = UserSettings()
            }
        } else {
            _userSettings.value = UserSettings()
        }
    }

    private fun saveUserSettings(settings: UserSettings) {
        _userSettings.value = settings
        sharedPreferences?.edit()?.putString("user_settings", settings.toJson().toString())?.apply()

        // Also save to Firestore
        viewModelScope.launch {
            userRepository.saveUserSettings(settings)
        }
    }

    private fun loadUserFromStorage() {
        // Check if user is authenticated
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            // User is logged in, sync with Firestore
            syncWithFirestore(firebaseUser.uid)
        } else {
            // No authenticated user, try to load from local storage for demo purposes
            // In production, you might want to force login instead
            val savedUserJson = sharedPreferences?.getString("user_data", null)
            if (savedUserJson != null && savedUserJson.isNotEmpty()) {
                try {
                    val savedUser = User.fromJson(savedUserJson)
                    _currentUser.value = savedUser
                } catch (e: Exception) {
                    // If loading fails, we'll wait for authentication
                    _errorMessage.value = "Please log in to continue"
                }
            }
        }
    }

    /**
     * Initialize user profile after authentication
     * Should be called after successful login/registration
     */
    fun initializeUserProfile(userId: String, email: String, displayName: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // Try to fetch existing user profile from Firestore
            userRepository.getUser(userId)
                .onSuccess { firestoreUser ->
                    if (firestoreUser != null) {
                        // User profile exists, load it
                        _currentUser.value = firestoreUser
                        saveUserToLocalStorage(firestoreUser)
                    } else {
                        // Create new user profile
                        val newUser = User(
                            userId = userId,
                            createdAt = System.currentTimeMillis(),
                            lastSeen = "Online",
                            fullName = displayName,
                            email = email,
                            phone = "",
                            description = "",
                            location = "",
                            socialLinks = emptyList()
                        )
                        _currentUser.value = newUser
                        saveUserToLocalStorage(newUser)
                        saveUserToFirestore(newUser)
                    }
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to load user profile: ${error.message}"
                }

            _isLoading.value = false
        }
    }

    private fun syncWithFirestore(userId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            userRepository.getUser(userId, forceRefresh)
                .onSuccess { firestoreUser ->
                    if (firestoreUser != null) {
                        // Update local data with Firestore data
                        _currentUser.value = firestoreUser
                        saveUserToLocalStorage(firestoreUser)
                    } else {
                        // User doesn't exist in Firestore, might need to create profile
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

    private fun saveUserToLocalStorage(user: User) {
        val editor = sharedPreferences?.edit()
        if (editor != null) {
            editor.putString("user_data", user.toJson())
            editor.apply()
        }
        updateUiState()
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
     * Force refresh user data from Firestore (bypasses cache)
     */
    fun refreshUserFromFirestore() {
        _currentUser.value?.userId?.let { userId ->
            syncWithFirestore(userId, forceRefresh = true)
        }
    }

    /**
     * Refresh user profile - gets fresh data from server
     */
    fun refreshUserProfile() {
        val userId = auth.currentUser?.uid ?: _currentUser.value?.userId
        if (userId != null) {
            syncWithFirestore(userId, forceRefresh = true)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Get user profile as JSON for NFC/QR sharing
     */
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
        json.put("description", user.description)
        json.put("location", user.location)
        json.put("socialLinks", SocialLink.listToJsonArray(user.socialLinks))
        json.put("timestamp", System.currentTimeMillis())

        return json
    }

    /**
     * Clear local user data (for logout)
     */
    fun clearUserData() {
        sharedPreferences?.edit()?.clear()?.apply()
    }

    /**
     * Save user profile with social links
     */
    fun saveUserProfileWithLinks(
        fullName: String,
        phone: String,
        email: String,
        description: String,
        location: String,
        socialLinks: List<SocialLink>
    ) {
        val current = _currentUser.value
        if (current != null) {
            val updatedUser = current.copy(
                fullName = fullName,
                phone = phone,
                email = email,
                description = description,
                location = location,
                socialLinks = socialLinks
            )
            _currentUser.value = updatedUser
            saveUserToLocalStorage(updatedUser)
            saveUserToFirestore(updatedUser)
            updateUiState()
        }
    }

    /**
     * Update location sharing preference
     */
    fun updateLocationSharingPreference(enabled: Boolean) {
        val current = _userSettings.value ?: UserSettings()
        val updated = current.copy(
            userId = _currentUser.value?.userId ?: "",
            isLocationShared = enabled
        )
        saveUserSettings(updated)
        updateUiState()
    }

    /**
     * Update notification preference
     */
    fun updateNotificationPreference(enabled: Boolean) {
        val current = _userSettings.value ?: UserSettings()
        val updated = current.copy(
            userId = _currentUser.value?.userId ?: "",
            isPushNotificationsEnabled = enabled
        )
        saveUserSettings(updated)
        updateUiState()
    }

    /**
     * Toggle location sharing preference
     */
    fun toggleLocationSharing() {
        val current = _userSettings.value ?: UserSettings()
        val updated = current.copy(
            userId = _currentUser.value?.userId ?: "",
            isLocationShared = !current.isLocationShared
        )
        saveUserSettings(updated)
        updateUiState()
    }

    /**
     * Add a new social link
     */
    fun addSocialLink(link: SocialLink) {
        val currentUser = _currentUser.value ?: return
        val updatedLinks = currentUser.socialLinks + link
        val updatedUser = currentUser.copy(socialLinks = updatedLinks)
        _currentUser.value = updatedUser
        saveUserToLocalStorage(updatedUser)
        saveUserToFirestore(updatedUser)
        updateUiState()
    }

    /**
     * Toggle visibility of a social link on profile
     */
    fun toggleVisibility(linkId: String) {
        val currentUser = _currentUser.value ?: return
        val updatedLinks = currentUser.socialLinks.map { link ->
            if (link.id == linkId) {
                link.copy(isVisibleOnProfile = !link.isVisibleOnProfile)
            } else {
                link
            }
        }
        val updatedUser = currentUser.copy(socialLinks = updatedLinks)
        _currentUser.value = updatedUser
        saveUserToLocalStorage(updatedUser)
        saveUserToFirestore(updatedUser)
        updateUiState()
    }

    /**
     * Update a social link
     */
    fun updateSocialLink(linkId: String, updatedLink: SocialLink) {
        val currentUser = _currentUser.value ?: return
        val updatedLinks = currentUser.socialLinks.map { link ->
            if (link.id == linkId) updatedLink else link
        }
        val updatedUser = currentUser.copy(socialLinks = updatedLinks)
        _currentUser.value = updatedUser
        saveUserToLocalStorage(updatedUser)
        saveUserToFirestore(updatedUser)
        updateUiState()
    }

    /**
     * Delete a social link
     */
    fun deleteSocialLink(linkId: String) {
        val currentUser = _currentUser.value ?: return
        val updatedLinks = currentUser.socialLinks.filter { it.id != linkId }
        val updatedUser = currentUser.copy(socialLinks = updatedLinks)
        _currentUser.value = updatedUser
        saveUserToLocalStorage(updatedUser)
        saveUserToFirestore(updatedUser)
        updateUiState()
    }

    /**
     * Check if location sharing is enabled
     */
    fun isLocationSharingEnabled(): Boolean {
        return _userSettings.value?.isLocationShared ?: true
    }

    /**
     * Sign out the current user
     */
    fun signOut() {
        auth.signOut()
        clearUserData()
    }
}


