package com.atdialer.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entities ──────────────────────────────────────────

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val contactName: String,
    val number: String,
    val hour: Int,           // 0-23
    val minute: Int,         // 0-59
    val repeatDays: String,  // "1,3,5" = Mon,Wed,Fri (1=Mon..7=Sun), "" = once
    val isEnabled: Boolean = true,
    val workerId: String = "" // WorkManager task ID
)

@Entity(tableName = "call_logs")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactName: String,
    val number: String,
    val startTime: Long,
    val endTime: Long = 0,
    val durationSeconds: Int = 0,
    val outcome: String = "UNKNOWN", // ANSWERED, NO_ANSWER, BUSY, FAILED, ONGOING
    val scheduleId: Long? = null,    // null = manual dial
    val notes: String = ""
)

// ── DAOs ──────────────────────────────────────────────

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAll(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY hour ASC, minute ASC")
    fun getAll(): Flow<List<Schedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): Schedule?

    @Query("UPDATE schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE schedules SET workerId = :workerId WHERE id = :id")
    suspend fun setWorkerId(id: Long, workerId: String)
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY startTime DESC")
    fun getAll(): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM call_logs ORDER BY startTime DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<CallLogEntry>>

    @Insert
    suspend fun insert(entry: CallLogEntry): Long

    @Update
    suspend fun update(entry: CallLogEntry)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getById(id: Long): CallLogEntry?

    @Query("""
        SELECT COUNT(*) as total,
               SUM(durationSeconds) as totalDuration,
               SUM(CASE WHEN outcome='ANSWERED' THEN 1 ELSE 0 END) as answered,
               SUM(CASE WHEN outcome='NO_ANSWER' THEN 1 ELSE 0 END) as noAnswer,
               SUM(CASE WHEN outcome='BUSY' THEN 1 ELSE 0 END) as busy
        FROM call_logs
    """)
    suspend fun getStats(): CallStats
}

data class CallStats(
    val total: Int,
    val totalDuration: Int?,
    val answered: Int,
    val noAnswer: Int,
    val busy: Int
)

// ── Database ──────────────────────────────────────────

@Database(
    entities = [Contact::class, Schedule::class, CallLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "atdialer.db")
                    .build().also { INSTANCE = it }
            }
    }
}
