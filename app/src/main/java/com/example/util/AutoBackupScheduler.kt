package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {
    private const val UNIQUE_WORK_NAME = "anwesha_auto_drive_backup"

    fun schedule(context: Context, frequency: String, wifiOnly: Boolean, enabled: Boolean) {
        try {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                Log.d("DRIVE_BACKUP", "AutoBackupScheduler: Cancelled auto backup work")
                return
            }

            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val (repeatInterval, timeUnit) = when (frequency.trim()) {
                "সাপ্তাহিক", "Weekly" -> Pair(7L, TimeUnit.DAYS)
                "ডাটা পরিবর্তন হলে", "Only when data changes" -> Pair(6L, TimeUnit.HOURS)
                else -> Pair(24L, TimeUnit.HOURS) // দৈনিক / Daily
            }

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(repeatInterval, timeUnit)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d("DRIVE_BACKUP", "AutoBackupScheduler: Scheduled periodic backup ($frequency, wifiOnly=$wifiOnly)")
        } catch (e: Exception) {
            Log.w("DRIVE_BACKUP", "AutoBackupScheduler: Could not schedule WorkManager: ${e.localizedMessage}")
        }
    }

    fun triggerOneTimeBackup(context: Context, wifiOnly: Boolean = false) {
        try {
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Log.d("DRIVE_BACKUP", "AutoBackupScheduler: Enqueued one-time change backup")
        } catch (e: Exception) {
            Log.w("DRIVE_BACKUP", "AutoBackupScheduler: Could not trigger one-time backup: ${e.localizedMessage}")
        }
    }
}
