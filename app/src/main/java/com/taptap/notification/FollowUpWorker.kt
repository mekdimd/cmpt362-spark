package com.taptap.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.taptap.repository.ConnectionRepository
import com.taptap.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FollowUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PREFIX = "follow_up_worker_"
        const val INPUT_CONNECTION_ID = "connection_id"
        const val INPUT_USER_ID = "user_id"
        const val INPUT_USER_NAME = "user_name"
        const val INPUT_USER_EMAIL = "user_email"
        const val INPUT_USER_PHONE = "user_phone"
        private const val TAG = "FollowUpWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d(TAG, "═══════════════════════════════════════════════")
            android.util.Log.d(TAG, "🔔 FOLLOW-UP WORKER STARTED")
            android.util.Log.d(TAG, "═══════════════════════════════════════════════")
            android.util.Log.d(TAG, "⏰ Triggered at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            android.util.Log.d(TAG, "🔄 Run attempt: $runAttemptCount")

            // Get connection details from input data
            val connectionId = inputData.getString(INPUT_CONNECTION_ID)
            val userId = inputData.getString(INPUT_USER_ID)
            val userName = inputData.getString(INPUT_USER_NAME)
            val userEmail = inputData.getString(INPUT_USER_EMAIL)
            val userPhone = inputData.getString(INPUT_USER_PHONE)

            android.util.Log.d(TAG, "📦 Input data retrieved:")
            android.util.Log.d(TAG, "   Connection ID: $connectionId")
            android.util.Log.d(TAG, "   User ID: $userId")
            android.util.Log.d(TAG, "   User Name: $userName")
            android.util.Log.d(TAG, "   Email: $userEmail")
            android.util.Log.d(TAG, "   Phone: $userPhone")

            if (connectionId.isNullOrEmpty() || userId.isNullOrEmpty() || userName.isNullOrEmpty()) {
                android.util.Log.e(TAG, "❌ ERROR: Missing required input data")
                android.util.Log.e(TAG, "═══════════════════════════════════════════════")
                return Result.failure()
            }

            android.util.Log.d(TAG, "✅ Input data validation passed")

            // Verify the connection still exists
            android.util.Log.d(TAG, "🔍 Verifying connection still exists...")
            val connectionResult = connectionRepository.getConnection(connectionId)
            if (connectionResult.isFailure || connectionResult.getOrNull() == null) {
                android.util.Log.w(TAG, "⚠️  Connection not found - user may have deleted it")
                android.util.Log.d(TAG, "✅ Skipping notification (no retry)")
                android.util.Log.d(TAG, "═══════════════════════════════════════════════")
                return Result.success() // Don't retry if connection was deleted
            }

            android.util.Log.d(TAG, "✅ Connection verified - still exists")

            // Check if notifications are enabled in user settings
            // This would ideally fetch from DataStore/SharedPreferences
            // For now, we'll show the notification

            android.util.Log.d(TAG, "🔔 Showing follow-up notification...")
            android.util.Log.d(TAG, "   Target: $userName")

            // Show the notification
            notificationHelper.showFollowUpNotification(
                connectionId = connectionId,
                userId = userId,
                userName = userName,
                userEmail = userEmail,
                userPhone = userPhone
            )

            android.util.Log.d(TAG, "✅ Notification shown successfully!")
            android.util.Log.d(TAG, "═══════════════════════════════════════════════")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "═══════════════════════════════════════════════")
            android.util.Log.e(TAG, "❌ ERROR in follow-up worker", e)
            android.util.Log.e(TAG, "   Exception: ${e.javaClass.simpleName}")
            android.util.Log.e(TAG, "   Message: ${e.message}")
            android.util.Log.e(TAG, "   Will retry...")
            android.util.Log.e(TAG, "═══════════════════════════════════════════════")
            // Retry on failure (up to 3 times by default)
            Result.retry()
        }
    }
}

