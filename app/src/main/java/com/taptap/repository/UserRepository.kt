package com.taptap.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.taptap.model.User
import com.taptap.model.UserSettings
import kotlinx.coroutines.tasks.await

/**
 * Repository layer for handling Firestore operations for User
 * Following MVVM architecture, this handles all data operations
 */
class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val settingsCollection = db.collection("user_settings")

    companion object {
        private const val TAG = "UserRepository"
    }

    /**
     * Save or update a user in Firestore
     * @param user The user to save
     * @return Result with success or error
     */
    suspend fun saveUser(user: User): Result<Unit> {
        return try {
            usersCollection
                .document(user.userId)
                .set(user.toMap(), SetOptions.merge())
                .await()

            Log.d(TAG, "User saved successfully: ${user.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve a user from Firestore by userId
     * @param userId The user ID (Firebase Auth UID) to retrieve
     * @param forceRefresh If true, bypasses cache and gets fresh data from server
     * @return Result with user or error
     */
    suspend fun getUser(userId: String, forceRefresh: Boolean = false): Result<User?> {
        return try {
            val source = if (forceRefresh) {
                com.google.firebase.firestore.Source.SERVER
            } else {
                com.google.firebase.firestore.Source.DEFAULT
            }

            val document = usersCollection
                .document(userId)
                .get(source)
                .await()

            if (document.exists()) {
                val user = User.fromMap(document.data as Map<String, Any>)
                Log.d(TAG, "User retrieved successfully: $userId (forceRefresh: $forceRefresh)")
                Result.success(user)
            } else {
                Log.d(TAG, "User not found: $userId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving user", e)
            Result.failure(e)
        }
    }

    /**
     * Update user's last seen status
     * @param userId The user ID (Firebase Auth UID)
     * @param lastSeen The last seen status
     */
    suspend fun updateLastSeen(userId: String, lastSeen: String): Result<Unit> {
        return try {
            usersCollection
                .document(userId)
                .update("lastSeen", lastSeen, "updatedAt", System.currentTimeMillis())
                .await()

            Log.d(TAG, "Last seen updated for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last seen", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a user from Firestore
     * @param userId The user ID (Firebase Auth UID) to delete
     */
    suspend fun deleteUser(userId: String): Result<Unit> {
        return try {
            usersCollection
                .document(userId)
                .delete()
                .await()

            Log.d(TAG, "User deleted successfully: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user", e)
            Result.failure(e)
        }
    }

    /**
     * Get all users from Firestore (useful for admin features or social features)
     * @return Result with list of users or error
     */
    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = usersCollection.get().await()
            val users = snapshot.documents.mapNotNull { document ->
                try {
                    User.fromMap(document.data as Map<String, Any>)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user document: ${document.id}", e)
                    null
                }
            }
            Log.d(TAG, "Retrieved ${users.size} users")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving all users", e)
            Result.failure(e)
        }
    }

    /**
     * Update user profile fields
     * @param userId The user ID
     * @param updates Map of field names to values
     */
    suspend fun updateUserFields(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            val updateData = updates.toMutableMap()
            updateData["updatedAt"] = System.currentTimeMillis()

            usersCollection
                .document(userId)
                .update(updateData)
                .await()

            Log.d(TAG, "User fields updated for: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user fields", e)
            Result.failure(e)
        }
    }

    /**
     * Save user settings to Firestore
     * @param settings The user settings to save
     * @return Result with success or error
     */
    suspend fun saveUserSettings(settings: UserSettings): Result<Unit> {
        return try {
            settingsCollection
                .document(settings.userId)
                .set(settings.toMap(), SetOptions.merge())
                .await()

            Log.d(TAG, "User settings saved successfully: ${settings.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user settings", e)
            Result.failure(e)
        }
    }

    /**
     * Get user settings from Firestore
     * @param userId The user ID
     * @return Result with settings or error
     */
    suspend fun getUserSettings(userId: String): Result<UserSettings?> {
        return try {
            val document = settingsCollection
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val settings = UserSettings.fromMap(document.data as Map<String, Any>)
                Log.d(TAG, "User settings retrieved successfully: $userId")
                Result.success(settings)
            } else {
                Log.d(TAG, "User settings not found: $userId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving user settings", e)
            Result.failure(e)
        }
    }
}
