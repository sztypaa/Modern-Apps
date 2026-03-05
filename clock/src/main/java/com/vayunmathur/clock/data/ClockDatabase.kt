package com.vayunmathur.clock.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vayunmathur.library.util.DatabaseItem
import com.vayunmathur.library.util.DefaultConverters
import com.vayunmathur.library.util.TrueDao
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Entity
data class Timer(
    var isRunning: Boolean,
    var name: String,
    var remainingStartTime: Instant,
    var remainingLength: Duration,
    var totalLength: Duration,
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
): DatabaseItem {
    fun stopped(): Timer {
        val now = Clock.System.now()
        val remainingTime = remainingLength - (now - remainingStartTime)
        return copy(isRunning = false, remainingLength = remainingTime)
    }
    fun started(): Timer {
        val now = Clock.System.now()
        return copy(isRunning = true, remainingStartTime = now)
    }
}

@Dao
interface TimerDao: TrueDao<Timer>

@TypeConverters(DefaultConverters::class)
@Database(entities = [Timer::class], version = 1)
abstract class ClockDatabase: RoomDatabase() {
    abstract fun timerDao(): TimerDao
}