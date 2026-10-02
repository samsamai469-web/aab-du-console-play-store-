package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            SmartNotificationManager.processScheduledNotification(appContext)
            Result.success()
        } catch (e: Exception) {
            Log.w("ReminderWorker", "Failed to process scheduled notification", e)
            Result.retry()
        }
    }
}
