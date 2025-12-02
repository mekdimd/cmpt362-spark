package com.taptap.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Repository for Firebase Authentication operations
 * Handles user registration, login, logout, and password management
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Get currently logged-in user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Check if user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Register new user with email and password
     * @param email User's email
     * @param password User's password
     * @param displayName User's display name
     * @return Result with FirebaseUser or error
     */
    suspend fun registerUser(
        email: String,
        password: String,
        displayName: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                // Set display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()

                user.updateProfile(profileUpdates).await()

                Log.d(TAG, "User registered successfully: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("User creation failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user", e)
            Result.failure(e)
        }
    }

    /**
     * Login user with email and password
     * @param email User's email
     * @param password User's password
     * @return Result with FirebaseUser or error
     */
    suspend fun loginUser(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                Log.d(TAG, "User logged in successfully: ${user.uid}")
                Result.success(user)
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in user", e)
            Result.failure(e)
        }
    }

    /**
     * Logout current user
     */
    fun logoutUser() {
        try {
            auth.signOut()
            Log.d(TAG, "User logged out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out user", e)
        }
    }

    /**
     * Send password reset email
     * @param email User's email
     * @return Result with success or error
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent to: $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending password reset email", e)
            Result.failure(e)
        }
    }

    /**
     * Update user's email
     * @param newEmail New email address
     * @return Result with success or error
     */
    suspend fun updateEmail(newEmail: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
            user.updateEmail(newEmail).await()
            Log.d(TAG, "Email updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating email", e)
            Result.failure(e)
        }
    }

    /**
     * Update user's password
     * @param newPassword New password
     * @return Result with success or error
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
            user.updatePassword(newPassword).await()
            Log.d(TAG, "Password updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating password", e)
            Result.failure(e)
        }
    }

    /**
     * Delete user account
     * @return Result with success or error
     */
    suspend fun deleteUser(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
            user.delete().await()
            Log.d(TAG, "User deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user", e)
            Result.failure(e)
        }
    }

    /**
     * Send email verification
     * @return Result with success or error
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
            user.sendEmailVerification().await()
            Log.d(TAG, "Verification email sent")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending verification email", e)
            Result.failure(e)
        }
    }
}

