package com.taptap.di

import android.content.Context
import com.taptap.notification.FollowUpScheduler
import com.taptap.notification.NotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing notification-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper {
        return NotificationHelper(context)
    }

    @Provides
    @Singleton
    fun provideFollowUpScheduler(
        @ApplicationContext context: Context
    ): FollowUpScheduler {
        return FollowUpScheduler(context)
    }
}

