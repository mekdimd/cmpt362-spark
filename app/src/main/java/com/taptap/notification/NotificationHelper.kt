package com.taptap.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.taptap.R

class NotificationHelper(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID_FOLLOW_UP = "follow_up_channel"
        const val CHANNEL_NAME_FOLLOW_UP = "Follow-up Reminders"
        const val NOTIFICATION_ID_FOLLOW_UP = 1001

        const val CHANNEL_ID_CONNECTION = "connection_channel"
        const val CHANNEL_NAME_CONNECTION = "New Connections"
        const val NOTIFICATION_ID_CONNECTION = 1002

        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_EMAIL = "extra_user_email"
        const val EXTRA_USER_PHONE = "extra_user_phone"
        const val EXTRA_CONNECTION_ID = "extra_connection_id"
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for Android O and above
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val followUpChannel = NotificationChannel(
                CHANNEL_ID_FOLLOW_UP,
                CHANNEL_NAME_FOLLOW_UP,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to follow up with your connections"
                enableVibration(true)
                enableLights(true)
            }

            val connectionChannel = NotificationChannel(
                CHANNEL_ID_CONNECTION,
                CHANNEL_NAME_CONNECTION,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when you connect with someone new"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(followUpChannel)
            notificationManager.createNotificationChannel(connectionChannel)
        }
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older versions
        }
    }

    /**
     * Show a follow-up reminder notification
     */
    fun showFollowUpNotification(
        connectionId: String,
        userId: String,
        userName: String,
        userEmail: String?,
        userPhone: String?
    ) {
        if (!hasNotificationPermission()) {
            android.util.Log.w("NotificationHelper", "Notification permission not granted")
            return
        }

        // Deep link intent to open profile screen
        val deepLinkIntent = createDeepLinkIntent(userId, userName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            connectionId.hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_FOLLOW_UP)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app icon
            .setContentTitle("Follow up with $userName")
            .setContentText("It's been a while! Reconnect with $userName today.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("It's been a while since you connected with $userName. Why not reach out and stay in touch?")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        // Add Call action if phone is available
        if (!userPhone.isNullOrEmpty()) {
            val callIntent = createCallIntent(userPhone)
            val callPendingIntent = PendingIntent.getActivity(
                context,
                (connectionId + "_call").hashCode(),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground, // Replace with call icon
                "Call",
                callPendingIntent
            )
        }

        // Add Email action if email is available
        if (!userEmail.isNullOrEmpty()) {
            val emailIntent = createEmailIntent(userEmail, userName)
            val emailPendingIntent = PendingIntent.getActivity(
                context,
                (connectionId + "_email").hashCode(),
                emailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground, // Replace with email icon
                "Email",
                emailPendingIntent
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(
                connectionId.hashCode(),
                builder.build()
            )
            android.util.Log.d("NotificationHelper", "Notification shown for $userName")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Failed to show notification", e)
        }
    }

    /**
     * Show a notification when a new connection is made
     */
    fun showConnectionNotification(
        connectionId: String,
        userId: String,
        userName: String,
        userEmail: String?,
        userPhone: String?
    ) {
        if (!hasNotificationPermission()) {
            android.util.Log.w("NotificationHelper", "Notification permission not granted")
            return
        }

        // Deep link intent to open profile screen
        val deepLinkIntent = createDeepLinkIntent(userId, userName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            connectionId.hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CONNECTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app icon
            .setContentTitle("Connected with $userName")
            .setContentText("You're now connected! Tap to view profile.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You've successfully connected with $userName. View their profile to see contact details and send a message.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        // Add Call action if phone is available
        if (!userPhone.isNullOrEmpty()) {
            val callIntent = createCallIntent(userPhone)
            val callPendingIntent = PendingIntent.getActivity(
                context,
                (connectionId + "_call").hashCode(),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground, // Replace with call icon
                "Call",
                callPendingIntent
            )
        }

        // Add Message action if phone is available
        if (!userPhone.isNullOrEmpty()) {
            val messageIntent = createMessageIntent(userPhone)
            val messagePendingIntent = PendingIntent.getActivity(
                context,
                (connectionId + "_message").hashCode(),
                messageIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground, // Replace with message icon
                "Message",
                messagePendingIntent
            )
        }

        // Add Email action if email is available
        if (!userEmail.isNullOrEmpty()) {
            val emailIntent = createEmailIntent(userEmail, userName)
            val emailPendingIntent = PendingIntent.getActivity(
                context,
                (connectionId + "_email").hashCode(),
                emailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground, // Replace with email icon
                "Email",
                emailPendingIntent
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(
                (connectionId + "_connection").hashCode(),
                builder.build()
            )
            android.util.Log.d("NotificationHelper", "Connection notification shown for $userName")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Failed to show connection notification", e)
        }
    }

    /**
     * Create deep link intent to open connection detail screen
     */
    private fun createDeepLinkIntent(userId: String, userName: String): Intent {
        // Create intent to launch MainActivity with deep link data
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myapp://connection/$userId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_USER_NAME, userName)
        }
        return intent
    }

    /**
     * Create call intent
     */
    private fun createCallIntent(phoneNumber: String): Intent {
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Create message/SMS intent
     */
    private fun createMessageIntent(phoneNumber: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Create email intent
     */
    private fun createEmailIntent(email: String, userName: String): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, "Following up - Spark")
            putExtra(Intent.EXTRA_TEXT, "Hi $userName,\n\nI wanted to reach out and reconnect with you.\n\nBest regards")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Cancel a specific notification
     */
    fun cancelNotification(connectionId: String) {
        NotificationManagerCompat.from(context).cancel(connectionId.hashCode())
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}

