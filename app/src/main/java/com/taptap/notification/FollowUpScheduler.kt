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
     * Schedule a follow-up reminder for a connection with custom time unit
     */
    fun scheduleFollowUpReminder(
        connection: Connection,
        delayValue: Int,
        delayUnit: String = "days"
    ) {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "SCHEDULING FOLLOW-UP REMINDER")
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "User: ${connection.connectedUserName}")
        Log.d(TAG, "Connection ID: ${connection.connectionId}")
        Log.d(TAG, "Email: ${connection.connectedUserEmail}")
        Log.d(TAG, "Phone: ${connection.connectedUserPhone}")
        Log.d(TAG, "Delay: $delayValue $delayUnit")
        Log.d(TAG, "═══════════════════════════════════════════════")

        val inputData = Data.Builder()
            .putString(FollowUpWorker.INPUT_CONNECTION_ID, connection.connectionId)
            .putString(FollowUpWorker.INPUT_USER_ID, connection.connectedUserId)
            .putString(FollowUpWorker.INPUT_USER_NAME, connection.connectedUserName)
            .putString(FollowUpWorker.INPUT_USER_EMAIL, connection.connectedUserEmail)
            .putString(FollowUpWorker.INPUT_USER_PHONE, connection.connectedUserPhone)
            .build()

        Log.d(TAG, "📦 Input data prepared for WorkManager")

        val timeUnit = when (delayUnit.lowercase()) {
            "minutes" -> TimeUnit.MINUTES
            "days" -> TimeUnit.DAYS
            "months" -> TimeUnit.DAYS
            else -> TimeUnit.DAYS
        }

        val adjustedDelay = if (delayUnit.lowercase() == "months") {
            delayValue * 30L
        } else {
            delayValue.toLong()
        }

        val followUpWork = OneTimeWorkRequestBuilder<FollowUpWorker>()
            .setInitialDelay(adjustedDelay, timeUnit)
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag("follow_up")
            .addTag(connection.connectionId)
            .build()

        Log.d(TAG, "WorkRequest created with ID: ${followUpWork.id}")

        try {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${FollowUpWorker.WORK_NAME_PREFIX}${connection.connectionId}",
                    ExistingWorkPolicy.REPLACE,
                    followUpWork
                )

            Log.d(TAG, "Follow-up work enqueued successfully!")
            Log.d(TAG, "Notification will trigger in $delayValue $delayUnit")

            val triggerTimeMillis = System.currentTimeMillis() + when (timeUnit) {
                TimeUnit.MINUTES -> adjustedDelay * 60 * 1000
                TimeUnit.DAYS -> adjustedDelay * 24 * 60 * 60 * 1000
                else -> adjustedDelay * 24 * 60 * 60 * 1000
            }
            val triggerDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(triggerTimeMillis))

            Log.d(TAG, "Will trigger at: $triggerDate")
            Log.d(TAG, "Current time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")

            if (delayUnit.lowercase() == "minutes") {
                val totalSeconds = adjustedDelay * 60
                Log.d(TAG, "COUNTDOWN: $totalSeconds seconds ($delayValue minutes)")
                Log.d(TAG, "TIP: Watch logcat for 'FollowUpWorker' to see when it triggers!")
            }

            Log.d(TAG, "═══════════════════════════════════════════════")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to enqueue follow-up work", e)
            Log.e(TAG, "═══════════════════════════════════════════════")
        }
    }

    /**
     * Cancel a scheduled follow-up reminder
     */
    fun cancelFollowUpReminder(connectionId: String) {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "CANCELLING FOLLOW-UP REMINDER")
        Log.d(TAG, "Connection ID: $connectionId")

        try {
            WorkManager.getInstance(context)
                .cancelUniqueWork("${FollowUpWorker.WORK_NAME_PREFIX}$connectionId")

            Log.d(TAG, "Follow-up cancelled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to cancel follow-up", e)
        }
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    /**
     * Cancel all follow-up reminders
     */
    fun cancelAllFollowUpReminders() {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "CANCELLING ALL FOLLOW-UP REMINDERS")

        try {
            WorkManager.getInstance(context)
                .cancelAllWorkByTag("follow_up")

            Log.d(TAG, "All follow-ups cancelled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to cancel all follow-ups", e)
        }
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    /**
     * Reschedule all existing follow-up reminders with a new delay
     * This should be called when the user changes the follow-up delay setting
     */
    suspend fun rescheduleAllFollowUps(newDelayDays: Int) {
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "RESCHEDULING ALL FOLLOW-UPS")
        Log.d(TAG, "New delay: $newDelayDays days")

        try {
            WorkManager.getInstance(context)
                .cancelAllWorkByTag("follow_up")

            Log.d(TAG, "All existing follow-ups cancelled")
            Log.d(
                TAG,
                "Note: You need to fetch all connections and reschedule them"
            )
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to reschedule follow-ups", e)
        }
        Log.d(TAG, "═══════════════════════════════════════════════")

    }
}
