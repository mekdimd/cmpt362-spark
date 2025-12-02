package com.taptap.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.taptap.model.Connection
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing connections between users
 * Handles Firestore operations for Connection data
 */
class ConnectionRepository {

    private val db = FirebaseFirestore.getInstance()
    private val connectionsCollection = db.collection("connections")

    companion object {
        private const val TAG = "ConnectionRepository"
    }

    /**
     * Save a new connection to Firestore
     * @param connection The connection to save
     * @return Result with success or error
     */
    suspend fun saveConnection(connection: Connection): Result<String> {
        return try {
            val docRef = connectionsCollection.document()
            val connectionWithId = connection.copy(connectionId = docRef.id)

            docRef.set(connectionWithId.toMap()).await()

            Log.d(TAG, "Connection saved successfully: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving connection", e)
            Result.failure(e)
        }
    }

    /**
     * Get all connections for a specific user
     * @param userId The user's Firebase Auth UID
     * @return Result with list of connections or error
     */
    suspend fun getUserConnections(userId: String): Result<List<Connection>> {
        return try {
            val snapshot = connectionsCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val connections = snapshot.documents.mapNotNull { doc ->
                try {
                    Connection.fromMap(doc.data as Map<String, Any>)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing connection document", e)
                    null
                }
            }

            Log.d(TAG, "Retrieved ${connections.size} connections for user: $userId")
            Result.success(connections)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving connections", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific connection by ID
     * @param connectionId The connection ID
     * @return Result with connection or error
     */
    suspend fun getConnection(connectionId: String): Result<Connection?> {
        return try {
            val document = connectionsCollection
                .document(connectionId)
                .get()
                .await()

            if (document.exists()) {
                val connection = Connection.fromMap(document.data as Map<String, Any>)
                Log.d(TAG, "Connection retrieved: $connectionId")
                Result.success(connection)
            } else {
                Log.d(TAG, "Connection not found: $connectionId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving connection", e)
            Result.failure(e)
        }
    }

    /**
     * Update connection notes
     * @param connectionId The connection ID
     * @param notes The updated notes
     * @return Result with success or error
     */
    suspend fun updateConnectionNotes(connectionId: String, notes: String): Result<Unit> {
        return try {
            connectionsCollection
                .document(connectionId)
                .update("notes", notes)
                .await()

            Log.d(TAG, "Connection notes updated: $connectionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating connection notes", e)
            Result.failure(e)
        }
    }

    /**
     * Update connection event information
     * @param connectionId The connection ID
     * @param eventName The event name
     * @param eventLocation The event location
     * @return Result with success or error
     */
    suspend fun updateConnectionEvent(
        connectionId: String,
        eventName: String,
        eventLocation: String
    ): Result<Unit> {
        return try {
            connectionsCollection
                .document(connectionId)
                .update(
                    mapOf(
                        "eventName" to eventName,
                        "eventLocation" to eventLocation
                    )
                )
                .await()

            Log.d(TAG, "Connection event updated: $connectionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating connection event", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a connection
     * @param connectionId The connection ID
     * @return Result with success or error
     */
    suspend fun deleteConnection(connectionId: String): Result<Unit> {
        return try {
            val connectionDoc = connectionsCollection
                .document(connectionId)
                .get()
                .await()

            val connection = connectionDoc.data?.let { Connection.fromMap(it) }

            if (connection != null) {
                connectionsCollection
                    .document(connectionId)
                    .delete()
                    .await()

                val reverseConnections = connectionsCollection
                    .whereEqualTo("userId", connection.connectedUserId)
                    .whereEqualTo("connectedUserId", connection.userId)
                    .get()
                    .await()

                reverseConnections.documents.forEach { doc ->
                    doc.reference.delete().await()
                    Log.d(TAG, "Deleted reverse connection: ${doc.id}")
                }

                Log.d(TAG, "Connection deleted bidirectionally: $connectionId")
            } else {
                connectionsCollection
                    .document(connectionId)
                    .delete()
                    .await()
                Log.d(TAG, "Connection deleted (no reverse found): $connectionId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting connection", e)
            Result.failure(e)
        }
    }

    /**
     * Get connections older than 2 months
     * @param userId The user's Firebase Auth UID
     * @return Result with list of old connections or error
     */
    suspend fun getOldConnections(userId: String): Result<List<Connection>> {
        return try {
            val twoMonthsAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)

            val snapshot = connectionsCollection
                .whereEqualTo("userId", userId)
                .whereLessThan("timestamp", twoMonthsAgo)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val connections = snapshot.documents.mapNotNull { doc ->
                try {
                    Connection.fromMap(doc.data as Map<String, Any>)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing connection document", e)
                    null
                }
            }

            Log.d(TAG, "Retrieved ${connections.size} old connections for user: $userId")
            Result.success(connections)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving old connections", e)
            Result.failure(e)
        }
    }

    /**
     * Get connection statistics for a user
     * @param userId The user's Firebase Auth UID
     * @return Result with connection count or error
     */
    suspend fun getConnectionCount(userId: String): Result<Int> {
        return try {
            val snapshot = connectionsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()

            Log.d(TAG, "Connection count: ${snapshot.size()}")
            Result.success(snapshot.size())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connection count", e)
            Result.failure(e)
        }
    }

    /**
     * Search connections by name
     * @param userId The user's Firebase Auth UID
     * @param searchQuery The search query
     * @return Result with matching connections or error
     */
    suspend fun searchConnections(userId: String, searchQuery: String): Result<List<Connection>> {
        return try {
            val allConnections = getUserConnections(userId).getOrNull() ?: emptyList()

            val filtered = allConnections.filter { connection ->
                connection.connectedUserName.contains(searchQuery, ignoreCase = true) ||
                connection.connectedUserEmail.contains(searchQuery, ignoreCase = true) ||
                connection.eventName.contains(searchQuery, ignoreCase = true)
            }

            Log.d(TAG, "Found ${filtered.size} matching connections")
            Result.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching connections", e)
            Result.failure(e)
        }
    }

    /**
     * Update connection with fresh profile data from Firestore
     * @param connectionId The connection ID
     * @param userData Map of user profile fields to update
     * @return Result with success or error
     */
    suspend fun updateConnectionProfile(
        connectionId: String,
        userData: Map<String, Any>
    ): Result<Unit> {
        return try {
            val updateData = mutableMapOf<String, Any>()

            userData["fullName"]?.let { updateData["connectedUserName"] = it }
            userData["email"]?.let { updateData["connectedUserEmail"] = it }
            userData["phone"]?.let { updateData["connectedUserPhone"] = it }
            userData["socialLinks"]?.let { updateData["connectedUserSocialLinks"] = it }
            userData["description"]?.let { updateData["connectedUserDescription"] = it }
            userData["location"]?.let { updateData["connectedUserLocation"] = it }

            connectionsCollection
                .document(connectionId)
                .update(updateData)
                .await()

            Log.d(TAG, "Connection profile updated: $connectionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating connection profile", e)
            Result.failure(e)
        }
    }
}

