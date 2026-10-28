package ru.vas1z.walkbreak.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.vas1z.walkbreak.data.local.dao.QuietPeriodDao
import ru.vas1z.walkbreak.data.local.dao.ReminderSlotDao
import ru.vas1z.walkbreak.data.local.dao.ScheduleSettingsDao
import ru.vas1z.walkbreak.data.local.dao.WalkSessionDao
import ru.vas1z.walkbreak.data.local.entity.QuietPeriodEntity
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity
import ru.vas1z.walkbreak.data.local.entity.ScheduleSettingsEntity
import ru.vas1z.walkbreak.data.local.entity.WalkSessionEntity

@Database(
    entities = [
        WalkSessionEntity::class,
        ScheduleSettingsEntity::class,
        QuietPeriodEntity::class,
        ReminderSlotEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class WalkBreakDatabase : RoomDatabase() {

    abstract fun walkSessionDao(): WalkSessionDao

    abstract fun scheduleSettingsDao(): ScheduleSettingsDao

    abstract fun quietPeriodDao(): QuietPeriodDao

    abstract fun reminderSlotDao(): ReminderSlotDao

    companion object {
        private const val DATABASE_NAME = "walk-break.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `schedule_settings` " +
                        "ADD COLUMN `pauseDuringWalk` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        @Volatile
        private var instance: WalkBreakDatabase? = null

        fun build(context: Context): WalkBreakDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalkBreakDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
