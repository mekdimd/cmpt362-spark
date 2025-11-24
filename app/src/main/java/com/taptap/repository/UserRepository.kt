package com.taptap.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.taptap.model.User
import kotlinx.coroutines.tasks.await

/**
 * Repository layer for handling Firestore operations for User
 * Following MVVM architecture, this handles all data operations
 */
class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

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
            val userMap = hashMapOf(
                "userId" to user.userId,
                "createdAt" to user.createdAt,
                "lastSeen" to user.lastSeen,
                "fullName" to user.fullName,
                "phone" to user.phone,
                "email" to user.email,
                "linkedIn" to user.linkedIn,
                "description" to user.description,
                "location" to user.location,
                "updatedAt" to System.currentTimeMillis()
            )

            usersCollection
                .document(user.userId.toString())
                .set(userMap, SetOptions.merge())
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
     * @param userId The user ID to retrieve
     * @return Result with user or error
     */
    suspend fun getUser(userId: Long): Result<User?> {
        return try {
            val document = usersCollection
                .document(userId.toString())
                .get()
                .await()

            if (document.exists()) {
                val user = User(
                    userId = document.getLong("userId") ?: 0,
                    createdAt = document.getLong("createdAt") ?: 0L,
                    lastSeen = document.getString("lastSeen") ?: "",
                    fullName = document.getString("fullName") ?: "",
                    phone = document.getString("phone") ?: "",
                    email = document.getString("email") ?: "",
                    linkedIn = document.getString("linkedIn") ?: "",
                    description = document.getString("description") ?: "",
                    location = document.getString("location") ?: ""
                )
                Log.d(TAG, "User retrieved successfully: $userId")
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
     * @param userId The user ID
     * @param lastSeen The last seen status
     */
    suspend fun updateLastSeen(userId: Long, lastSeen: String): Result<Unit> {
        return try {
            usersCollection
                .document(userId.toString())
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
     * @param userId The user ID to delete
     */
    suspend fun deleteUser(userId: Long): Result<Unit> {
        return try {
            usersCollection
                .document(userId.toString())
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
                    User(
                        userId = document.getLong("userId") ?: 0,
                        createdAt = document.getLong("createdAt") ?: 0L,
                        lastSeen = document.getString("lastSeen") ?: "",
                        fullName = document.getString("fullName") ?: "",
                        phone = document.getString("phone") ?: "",
                        email = document.getString("email") ?: "",
                        linkedIn = document.getString("linkedIn") ?: "",
                        description = document.getString("description") ?: "",
                        location = document.getString("location") ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user document: ${document.id}", e)
                    null
                }
            }
            Log.d(TAG, "Retrieved ${users.size} users")
            Result.success(users)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users", e)
            Result.failure(e)
        }
    }
}

