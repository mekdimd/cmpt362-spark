package com.taptap.notification

import android.content.Context
import android.util.Log
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
            Log.d(TAG, " FOLLOW-UP WORKER STARTED")
            Log.d(TAG, " Run attempt: $runAttemptCount")
            val connectionId = inputData.getString(INPUT_CONNECTION_ID)
            val userId = inputData.getString(INPUT_USER_ID)
            val userName = inputData.getString(INPUT_USER_NAME)
            val userEmail = inputData.getString(INPUT_USER_EMAIL)
            val userPhone = inputData.getString(INPUT_USER_PHONE)

            Log.d(TAG, "Input data retrieved:")
            Log.d(TAG, "   Connection ID: $connectionId")
            Log.d(TAG, "   User ID: $userId")
            Log.d(TAG, "   User Name: $userName")
            Log.d(TAG, "   Email: $userEmail")
            Log.d(TAG, "   Phone: $userPhone")

            if (connectionId.isNullOrEmpty() || userId.isNullOrEmpty() || userName.isNullOrEmpty()) {
                Log.e(TAG, "ERROR: Missing required input data")
                return Result.failure()
            }

            Log.d(TAG, "  Input data validation passed")

            Log.d(TAG, "  Verifying connection still exists...")
            val connectionResult = connectionRepository.getConnection(connectionId)
            if (connectionResult.isFailure || connectionResult.getOrNull() == null) {
                Log.w(TAG, "  Connection not found - user may have deleted it")
                Log.d(TAG, " Skipping notification (no retry)")
                
                return Result.success()
            }

            Log.d(TAG, "Connection verified - still exists")


            Log.d(TAG, "Showing follow-up notification...")
            Log.d(TAG, "   Target: $userName")

            notificationHelper.showFollowUpNotification(
                connectionId = connectionId,
                userId = userId,
                userName = userName,
                userEmail = userEmail,
                userPhone = userPhone
            )

            Log.d(TAG, "  Notification shown successfully!")
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in follow-up worker", e)
            Log.e(TAG, "   Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "   Message: ${e.message}")
            Log.e(TAG, "   Will retry...")
            Result.retry()
        }
    }
}

