package com.atdialer.app.service

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.atdialer.app.data.AppDatabase
import com.atdialer.app.data.Schedule
import java.util.*
import java.util.concurrent.TimeUnit

class ScheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong("scheduleId", -1)
        if (scheduleId < 0) return Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        val schedule = db.scheduleDao().getById(scheduleId) ?: return Result.failure()
        if (!schedule.isEnabled) return Result.success()

        // Fire the call
        val intent = Intent(applicationContext, DialerService::class.java).apply {
            action = DialerService.ACTION_DIAL
            putExtra(DialerService.EXTRA_NUMBER, schedule.number)
            putExtra(DialerService.EXTRA_NAME, schedule.contactName)
            putExtra(DialerService.EXTRA_SCHEDULE_ID, scheduleId)
        }
        applicationContext.startForegroundService(intent)

        // If repeating, reschedule for next occurrence
        if (schedule.repeatDays.isNotEmpty()) {
            val delay = calcNextDelay(schedule)
            schedule(applicationContext, schedule, delay)
        }

        return Result.success()
    }

    companion object {

        fun schedule(context: Context, schedule: Schedule, customDelayMs: Long? = null) {
            val delay = customDelayMs ?: calcNextDelay(schedule)
            if (delay < 0) return

            val data = workDataOf("scheduleId" to schedule.id)
            val tag  = "schedule_${schedule.id}"

            val request = OneTimeWorkRequestBuilder<ScheduleWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tag)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, scheduleId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("schedule_$scheduleId")
        }

        private fun calcNextDelay(schedule: Schedule): Long {
            val now  = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, schedule.hour)
                set(Calendar.MINUTE, schedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If repeating days, find next valid day
            if (schedule.repeatDays.isNotEmpty()) {
                val days = schedule.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                // days: 1=Mon,2=Tue,...,7=Sun (Calendar: 1=Sun,2=Mon,...,7=Sat)
                var found = false
                for (offset in 0..7) {
                    val candidate = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_MONTH, offset)
                        set(Calendar.HOUR_OF_DAY, schedule.hour)
                        set(Calendar.MINUTE, schedule.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // Convert Calendar day to our 1=Mon format
                    val calDay    = candidate.get(Calendar.DAY_OF_WEEK)
                    val ourDay    = if (calDay == Calendar.SUNDAY) 7 else calDay - 1
                    if (ourDay in days && candidate.timeInMillis > now.timeInMillis) {
                        return candidate.timeInMillis - now.timeInMillis
                    }
                }
            }

            // One-time: if time has passed today, schedule for tomorrow
            if (next.timeInMillis <= now.timeInMillis) {
                next.add(Calendar.DAY_OF_MONTH, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
