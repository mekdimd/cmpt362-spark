package com.taptap.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.taptap.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for handling authentication state and operations
 * Follows MVVM architecture pattern
 */
class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _isLoggedIn = MutableLiveData<Boolean>()
    val isLoggedIn: LiveData<Boolean> = _isLoggedIn

    init {
        checkAuthStatus()
    }

    /**
     * Check if user is currently logged in
     */
    fun checkAuthStatus() {
        val user = authRepository.getCurrentUser()
        _currentUser.value = user
        _isLoggedIn.value = user != null
    }

    /**
     * Register a new user
     * @param email User's email
     * @param password User's password
     * @param displayName User's display name
     */
    fun registerUser(email: String, password: String, displayName: String) {
        if (!validateRegistrationInput(email, password, displayName)) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.registerUser(email, password, displayName)
                .onSuccess { user ->
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    _successMessage.value = "Registration successful! Welcome, $displayName"

                    sendEmailVerification()
                }
                .onFailure { error ->
                    _errorMessage.value = getErrorMessage(error)
                }

            _isLoading.value = false
        }
    }

    /**
     * Login user with email and password
     * @param email User's email
     * @param password User's password
     */
    fun loginUser(email: String, password: String) {
        if (!validateLoginInput(email, password)) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.loginUser(email, password)
                .onSuccess { user ->
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    _successMessage.value = "Login successful! Welcome back"
                }
                .onFailure { error ->
                    _errorMessage.value = getErrorMessage(error)
                }

            _isLoading.value = false
        }
    }

    /**
     * Logout current user
     */
    fun logoutUser() {
        authRepository.logoutUser()
        _currentUser.value = null
        _isLoggedIn.value = false
        _successMessage.value = "Logged out successfully"
    }

    /**
     * Send password reset email
     * @param email User's email
     */
    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email address"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _errorMessage.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    _successMessage.value = "Password reset email sent! Check your inbox"
                }
                .onFailure { error ->
                    _errorMessage.value = getErrorMessage(error)
                }

            _isLoading.value = false
        }
    }

    /**
     * Send email verification
     */
    fun sendEmailVerification() {
        viewModelScope.launch {
            authRepository.sendEmailVerification()
                .onSuccess {
                    _successMessage.value = "Verification email sent!"
                }
                .onFailure { error ->
                    _errorMessage.value = "Failed to send verification email: ${error.message}"
                }
        }
    }

    /**
     * Update user's email
     * @param newEmail New email address
     */
    fun updateEmail(newEmail: String) {
        if (newEmail.isBlank()) {
            _errorMessage.value = "Please enter a new email address"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            _errorMessage.value = "Please enter a valid email address"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.updateEmail(newEmail)
                .onSuccess {
                    _successMessage.value = "Email updated successfully"
                    checkAuthStatus()
                }
                .onFailure { error ->
                    _errorMessage.value = getErrorMessage(error)
                }

            _isLoading.value = false
        }
    }

    /**
     * Update user's password
     * @param newPassword New password
     */
    fun updatePassword(newPassword: String) {
        if (newPassword.isBlank()) {
            _errorMessage.value = "Please enter a new password"
            return
        }

        if (newPassword.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.updatePassword(newPassword)
                .onSuccess {
                    _successMessage.value = "Password updated successfully"
                }
                .onFailure { error ->
                    _errorMessage.value = getErrorMessage(error)
                }

            _isLoading.value = false
        }
    }

    /**
     * Delete user account
     */
    fun deleteUser() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            authRepository.deleteUser()
                .onSuccess {
                    _currentUser.value = null
                    _isLoggedIn.value = false
                    _successMessage.value = "Account deleted successfully"
                }
                .onFailure { error ->
                    _errorMessage.value = getErrorMessage(error)
                }

            _isLoading.value = false
        }
    }

    /**
     * Validate registration input
     */
    private fun validateRegistrationInput(
        email: String,
        password: String,
        displayName: String
    ): Boolean {
        when {
            displayName.isBlank() -> {
                _errorMessage.value = "Please enter your name"
                return false
            }
            email.isBlank() -> {
                _errorMessage.value = "Please enter your email"
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _errorMessage.value = "Please enter a valid email address"
                return false
            }
            password.isBlank() -> {
                _errorMessage.value = "Please enter a password"
                return false
            }
            password.length < 6 -> {
                _errorMessage.value = "Password must be at least 6 characters"
                return false
            }
        }
        return true
    }

    /**
     * Validate login input
     */
    private fun validateLoginInput(email: String, password: String): Boolean {
        when {
            email.isBlank() -> {
                _errorMessage.value = "Please enter your email"
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _errorMessage.value = "Please enter a valid email address"
                return false
            }
            password.isBlank() -> {
                _errorMessage.value = "Please enter a password"
                return false
            }
        }
        return true
    }

    /**
     * Get user-friendly error message
     */
    private fun getErrorMessage(error: Throwable): String {
        return when {
            error.message?.contains("The email address is already in use") == true ->
                "This email is already registered"
            error.message?.contains("The email address is badly formatted") == true ->
                "Invalid email address"
            error.message?.contains("The password is invalid") == true ->
                "Invalid password"
            error.message?.contains("There is no user record") == true ->
                "No account found with this email"
            error.message?.contains("The user account has been disabled") == true ->
                "This account has been disabled"
            error.message?.contains("A network error") == true ->
                "Network error. Please check your connection"
            error.message?.contains("too many requests") == true ->
                "Too many attempts. Please try again later"
            else -> "Authentication failed: ${error.message}"
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _successMessage.value = null
    }

    /**
     * Get current user's UID
     */
    fun getCurrentUserId(): String? {
        return _currentUser.value?.uid
    }

    /**
     * Get current user's email
     */
    fun getCurrentUserEmail(): String? {
        return _currentUser.value?.email
    }

    /**
     * Get current user's display name
     */
    fun getCurrentUserDisplayName(): String? {
        return _currentUser.value?.displayName
    }

    /**
     * Check if email is verified
     */
    fun isEmailVerified(): Boolean {
        return _currentUser.value?.isEmailVerified == true
    }
}

