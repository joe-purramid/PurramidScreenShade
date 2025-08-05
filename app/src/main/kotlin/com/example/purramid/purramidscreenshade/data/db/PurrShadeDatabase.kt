// PurramidDatabase.kt
package com.example.purramid.thepurramid.data.db 

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.UUID


/**
 * The Room database for Purramid Class Tools application.
 * This contains tables for Clock, Randomizers, Screen Mask, Spotlight, Timers, and Traffic Light features.
 */
@Database(
    entities = [
        // List all your entity classes here
        CityEntity::class,
        ClockAlarmEntity::class,
        ClockStateEntity::class,
        RandomizerInstanceEntity::class,
        ScreenMaskStateEntity::class,
        SpinItemEntity::class,
        SpinListEntity::class,
        SpinSettingsEntity::class,
        SpotlightInstanceEntity::class,
        SpotlightOpeningEntity::class,
        SpotlightStateEntity::class,
        TimerStateEntity::class,
        TimeZoneBoundaryEntity::class,
        TrafficLightStateEntity::class
    ],
    version = 2, // Release to production
    exportSchema = false // Set to true if you want to export the schema to a file for version control (recommended for production apps)
)
@TypeConverters(Converters::class) // Register the TypeConverters class
abstract class PurramidDatabase : RoomDatabase() {

    /**
     * Abstract function to get the Data Access Objects
     * Room will generate the implementation.
     */
    abstract fun cityDao(): CityDao
    abstract fun clockAlarmDao(): ClockAlarmDao
    abstract fun clockDao(): ClockDao
    abstract fun randomizerDao(): RandomizerDao
    abstract fun screenMaskDao(): ScreenMaskDao
    abstract fun spotlightDao(): SpotlightDao
    abstract fun timerDao(): TimerDao
    abstract fun timeZoneDao(): TimeZoneDao
    abstract fun trafficLightDao(): TrafficLightDao

    companion object {
        @Volatile
        private var INSTANCE: PurramidDatabase? = null

        // Migration from 1 to 2: Add UUID to screen_mask_state
        private val MIGRATION_1_2 = object : Migration(1, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screen_mask_state ADD COLUMN uuid TEXT NOT NULL DEFAULT '${UUID.randomUUID()}'")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN isNested INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN nestedX INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN nestedY INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN soundsEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN selectedSoundUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN musicUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN recentMusicUrlsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE timer_state ADD COLUMN showLapTimes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clock_state RENAME COLUMN clockId TO instanceId")
                db.execSQL("ALTER TABLE clock_alarms RENAME COLUMN clockId TO instanceId")
                db.execSQL("ALTER TABLE randomizer_instances ADD COLUMN windowState TEXT NOT NULL DEFAULT 'normal'")
            }
        }

        // Future migrations would be added here

        fun getDatabase(context: Context): PurramidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PurramidDatabase::class.java,
                    "purramid_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        // Add future migrations here
                    )
                    .fallbackToDestructiveMigrationOnDowngrade(false) // Only destroy on downgrade
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}