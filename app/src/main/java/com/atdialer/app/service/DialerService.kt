package com.atdialer.app.service

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.atdialer.app.MainActivity
import com.atdialer.app.R
import com.atdialer.app.data.AppDatabase
import com.atdialer.app.data.CallLogEntry
import kotlinx.coroutines.*

class DialerService : Service() {

    companion object {
        const val ACTION_DIAL        = "com.atdialer.DIAL"
        const val ACTION_HANGUP      = "com.atdialer.HANGUP"
        const val EXTRA_NUMBER       = "number"
        const val EXTRA_NAME         = "name"
        const val EXTRA_SCHEDULE_ID  = "scheduleId"
        const val EXTRA_LOG_ID       = "logId"
        const val CHANNEL_ID         = "dialer_channel"
        const val NOTIF_ID           = 1

        // Broadcast for UI updates
        const val BROADCAST_CALL_STATE = "com.atdialer.CALL_STATE"
        const val EXTRA_STATE          = "state"  // DIALING, ANSWERED, ENDED

        var currentLogId: Long = -1L
        var callStartTime: Long = 0L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DIAL -> {
                val number     = intent.getStringExtra(EXTRA_NUMBER) ?: return START_NOT_STICKY
                val name       = intent.getStringExtra(EXTRA_NAME) ?: number
                val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1).takeIf { it >= 0 }

                startForeground(NOTIF_ID, buildNotification("Dialing $name..."))
                dialNumber(number, name, scheduleId)
            }
            ACTION_HANGUP -> {
                endCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun dialNumber(number: String, name: String, scheduleId: Long?) {
        scope.launch {
            // Insert call log entry
            val db = AppDatabase.getInstance(applicationContext)
            val logId = db.callLogDao().insert(
                CallLogEntry(
                    contactName = name,
                    number = number,
                    startTime = System.currentTimeMillis(),
                    outcome = "ONGOING",
                    scheduleId = scheduleId
                )
            )
            currentLogId = logId
            callStartTime = System.currentTimeMillis()

            // Broadcast to UI
            broadcastState("DIALING", logId)

            // Place call on main thread
            withContext(Dispatchers.Main) {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(callIntent)
            }
        }
    }

    private fun endCall() {
        if (currentLogId < 0) return
        scope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val entry = db.callLogDao().getById(currentLogId) ?: return@launch
            val duration = ((System.currentTimeMillis() - callStartTime) / 1000).toInt()
            db.callLogDao().update(entry.copy(
                endTime = System.currentTimeMillis(),
                durationSeconds = duration,
                outcome = if (duration > 3) "ANSWERED" else "NO_ANSWER"
            ))
            broadcastState("ENDED", currentLogId)
            currentLogId = -1L
        }
    }

    private fun broadcastState(state: String, logId: Long) {
        sendBroadcast(Intent(BROADCAST_CALL_STATE).apply {
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_LOG_ID, logId)
        })
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AT Dialer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Dialer Service", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active call management" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

// ── Call State Receiver ───────────────────────────────

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DialerService.currentLogId < 0) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended — update log
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(context)
                    val entry = db.callLogDao().getById(DialerService.currentLogId) ?: return@launch
                    val duration = ((System.currentTimeMillis() - DialerService.callStartTime) / 1000).toInt()
                    val outcome = when {
                        duration > 5  -> "ANSWERED"
                        duration > 0  -> "NO_ANSWER"
                        else          -> "FAILED"
                    }
                    db.callLogDao().update(entry.copy(
                        endTime = System.currentTimeMillis(),
                        durationSeconds = duration,
                        outcome = outcome
                    ))
                    DialerService.currentLogId = -1L
                    context.sendBroadcast(Intent(DialerService.BROADCAST_CALL_STATE).apply {
                        putExtra(DialerService.EXTRA_STATE, "ENDED")
                    })
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                DialerService.callStartTime = System.currentTimeMillis()
            }
        }
    }
}

// ── Boot Receiver ──────────────────────────────────────

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule all enabled schedules after reboot
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                db.scheduleDao().getAll().collect { schedules ->
                    schedules.filter { it.isEnabled }.forEach { schedule ->
                        ScheduleWorker.schedule(context, schedule)
                    }
                }
            }
        }
    }
}
