package com.example.sync.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Scheduler for Periodic, Debounced Immediate, and Generational Backup Works.
 */
object SyncWorkManager {

    private const val PERIODIC_SYNC_TAG = "school_periodic_sync"
    private const val IMMEDIATE_SYNC_TAG = "school_immediate_sync"
    private const val BACKUP_WORK_TAG = "school_daily_backup"

    /**
     * Enqueues a periodic sync running every 15-30 minutes under network connection.
     */
    fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // 5 min flex
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    /**
     * Triggers a debounced one-time sync whenever a local Room record changes.
     */
    fun triggerImmediateSync(context: Context, debounceSeconds: Long = 5) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateRequest = OneTimeWorkRequestBuilder<ImmediateSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(debounceSeconds, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_SYNC_TAG,
            ExistingWorkPolicy.REPLACE, // Replace debounces rapid successive keystrokes/edits
            immediateRequest
        )
    }

    /**
     * Enqueues daily generational backup work.
     */
    fun scheduleDailyBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKUP_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
