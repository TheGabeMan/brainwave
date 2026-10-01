package dev.gabrie.brainwave.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Brainwave::class], version = 2, exportSchema = true)
abstract class BrainwaveDatabase : RoomDatabase() {

    abstract fun brainwaveDao(): BrainwaveDao

    companion object {
        fun build(context: Context): BrainwaveDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                BrainwaveDatabase::class.java,
                "brainwaves.db",
            )
                // No fallbackToDestructiveMigration: these are people's own notes.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
