package com.atdialer.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atdialer.app.data.*
import com.atdialer.app.service.DialerService
import com.atdialer.app.service.ScheduleWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val contacts = db.contactDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedules = db.scheduleDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs = db.callLogDao().getRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Contacts ─────────────────────────────────────
    fun addContact(name: String, number: String, notes: String = "") = viewModelScope.launch {
        db.contactDao().insert(Contact(name = name, number = number, notes = notes))
    }

    fun deleteContact(contact: Contact) = viewModelScope.launch {
        db.contactDao().delete(contact)
    }

    // ── Dialing ───────────────────────────────────────
    fun dial(number: String, name: String) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, DialerService::class.java).apply {
            action = DialerService.ACTION_DIAL
            putExtra(DialerService.EXTRA_NUMBER, number)
            putExtra(DialerService.EXTRA_NAME, name)
        }
        ctx.startForegroundService(intent)
    }

    // ── Schedules ─────────────────────────────────────
    fun addSchedule(schedule: Schedule) = viewModelScope.launch {
        val id = db.scheduleDao().insert(schedule)
        val saved = schedule.copy(id = id)
        ScheduleWorker.schedule(getApplication(), saved)
        db.scheduleDao().setWorkerId(id, "schedule_$id")
    }

    fun toggleSchedule(schedule: Schedule, enabled: Boolean) = viewModelScope.launch {
        db.scheduleDao().setEnabled(schedule.id, enabled)
        if (enabled) {
            ScheduleWorker.schedule(getApplication(), schedule.copy(isEnabled = true))
        } else {
            ScheduleWorker.cancel(getApplication(), schedule.id)
        }
    }

    fun deleteSchedule(schedule: Schedule) = viewModelScope.launch {
        ScheduleWorker.cancel(getApplication(), schedule.id)
        db.scheduleDao().delete(schedule)
    }

    // ── Call Log ──────────────────────────────────────
    fun deleteLogEntry(entry: CallLogEntry) = viewModelScope.launch {
        db.callLogDao().deleteById(entry.id)
    }

    fun clearLog() = viewModelScope.launch {
        db.callLogDao().deleteAll()
    }

    suspend fun getStats(): CallStats = db.callLogDao().getStats()
}
