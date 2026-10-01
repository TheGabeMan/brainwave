package dev.gabrie.brainwave.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: the emailed calendar invite is gone, replaced by a direct entry in
 * the phone's calendar.
 *
 * `inviteSent`, `uid` and `icsSequence` only existed to support the invite, and
 * `calendarEventId` is new. SQLite before 3.35 (that is, everything below
 * Android 14) cannot drop a column, so the table is rebuilt: create the new
 * shape, copy the surviving columns across, swap.
 *
 * Existing rows keep `calendarEventId = NULL`. They were never in the calendar,
 * and they are not retro-fitted — the entry appears the next time a brainwave
 * is edited and saved.
 *
 * The CREATE statement is copied from the exported schema (schemas/…/2.json),
 * because Room validates the result column-for-column and a near miss refuses
 * to open the database at all.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `brainwaves_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`audioPath` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`dueAt` INTEGER, " +
                "`dueHasTime` INTEGER NOT NULL, " +
                "`completed` INTEGER NOT NULL, " +
                "`completedAt` INTEGER, " +
                "`noteMailSent` INTEGER NOT NULL, " +
                "`calendarEventId` INTEGER)"
        )
        db.execSQL(
            "INSERT INTO `brainwaves_new` " +
                "(id, title, body, audioPath, createdAt, dueAt, dueHasTime, completed, completedAt, noteMailSent) " +
                "SELECT id, title, body, audioPath, createdAt, dueAt, dueHasTime, completed, completedAt, noteMailSent " +
                "FROM `brainwaves`"
        )
        db.execSQL("DROP TABLE `brainwaves`")
        db.execSQL("ALTER TABLE `brainwaves_new` RENAME TO `brainwaves`")
    }
}
