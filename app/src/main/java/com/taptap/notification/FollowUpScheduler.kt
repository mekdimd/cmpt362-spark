package com.taptap.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import com.taptap.model.Connection
import java.util.concurrent.TimeUnit

class FollowUpScheduler(
    private val context: Context
) {
    companion object {
        const val DEFAULT_FOLLOW_UP_DAYS = 30
        private const val TAG = "FollowUpScheduler"
    }

    /**
     * Schedule a follow-up reminder for a connection
     */
    fun scheduleFollowUpReminder(
        connection: Connection,
        delayDays: Int = DEFAULT_FOLLOW_UP_DAYS
    ) {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "📅 SCHEDULING FOLLOW-UP REMINDER")
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "👤 User: ${connection.connectedUserName}")
        Log.d(TAG, "🆔 Connection ID: ${connection.connectionId}")
        Log.d(TAG, "📧 Email: ${connection.connectedUserEmail}")
        Log.d(TAG, "📱 Phone: ${connection.connectedUserPhone}")
        Log.d(TAG, "⏰ Delay: $delayDays day(s)")
        Log.d(TAG, "═══════════════════════════════════════════════")

        val inputData = Data.Builder()
            .putString(FollowUpWorker.INPUT_CONNECTION_ID, connection.connectionId)
            .putString(FollowUpWorker.INPUT_USER_ID, connection.connectedUserId)
            .putString(FollowUpWorker.INPUT_USER_NAME, connection.connectedUserName)
            .putString(FollowUpWorker.INPUT_USER_EMAIL, connection.connectedUserEmail)
            .putString(FollowUpWorker.INPUT_USER_PHONE, connection.connectedUserPhone)
            .build()

        Log.d(TAG, "📦 Input data prepared for WorkManager")

        val followUpWork = OneTimeWorkRequestBuilder<FollowUpWorker>()
            .setInitialDelay(delayDays.toLong(), TimeUnit.DAYS)
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag("follow_up")
            .addTag(connection.connectionId)
            .build()

        Log.d(TAG, "🔨 WorkRequest created with ID: ${followUpWork.id}")

        try {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${FollowUpWorker.WORK_NAME_PREFIX}${connection.connectionId}",
                    ExistingWorkPolicy.REPLACE,
                    followUpWork
                )

            Log.d(TAG, "✅ Follow-up work enqueued successfully!")
            Log.d(TAG, "🔔 Notification will trigger in $delayDays day(s)")
            Log.d(TAG, "═══════════════════════════════════════════════")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: Failed to enqueue follow-up work", e)
            Log.e(TAG, "═══════════════════════════════════════════════")
        }
    }

    /**
     * Cancel a scheduled follow-up reminder
     */
    fun cancelFollowUpReminder(connectionId: String) {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "🚫 CANCELLING FOLLOW-UP REMINDER")
        Log.d(TAG, "🆔 Connection ID: $connectionId")

        try {
            WorkManager.getInstance(context)
                .cancelUniqueWork("${FollowUpWorker.WORK_NAME_PREFIX}$connectionId")

            Log.d(TAG, "✅ Follow-up cancelled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: Failed to cancel follow-up", e)
        }
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    /**
     * Cancel all follow-up reminders
     */
    fun cancelAllFollowUpReminders() {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "🚫 CANCELLING ALL FOLLOW-UP REMINDERS")

        try {
            WorkManager.getInstance(context)
                .cancelAllWorkByTag("follow_up")

            Log.d(TAG, "✅ All follow-ups cancelled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: Failed to cancel all follow-ups", e)
        }
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    /**
     * Reschedule all existing follow-up reminders with a new delay
     * This should be called when the user changes the follow-up delay setting
     */
    suspend fun rescheduleAllFollowUps(newDelayDays: Int) {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "🔄 RESCHEDULING ALL FOLLOW-UPS")
        Log.d(TAG, "⏰ New delay: $newDelayDays days")

        try {
            // Cancel all existing follow-up work
            WorkManager.getInstance(context)
                .cancelAllWorkByTag("follow_up")

            Log.d(TAG, "✅ All existing follow-ups cancelled")
            Log.d(
                TAG,
                "ℹ️  Note: You need to fetch all connections and reschedule them"
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: Failed to reschedule follow-ups", e)
        }
        Log.d(TAG, "═══════════════════════════════════════════════")

        // Note: In a real implementation, you would fetch all connections from the repository
        // and reschedule each one with the new delay
        // This is left as an exercise based on your specific ConnectionRepository implementation
    }
}

