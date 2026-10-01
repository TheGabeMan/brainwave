package dev.gabrie.brainwave.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One captured thought.
 *
 * Times are stored as epoch milliseconds so Room needs no type converters.
 * [dueAt] is null when the recording contained no date and the user skipped
 * adding one; [dueHasTime] distinguishes "Friday" (all-day) from "Friday 3pm".
 */
@Entity(tableName = "brainwaves")
data class Brainwave(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    val audioPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val dueHasTime: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    /** Set once the "[brainwave] <title>" mail with the audio attachment went out. */
    val noteMailSent: Boolean = false,
    /**
     * The id of this brainwave's entry in the phone's calendar, so an edit
     * updates that entry and a delete removes it. Null when there is no entry —
     * no due date, calendar writing switched off, or permission not granted.
     */
    val calendarEventId: Long? = null,
)

enum class SortField { DUE_DATE, TITLE }

data class SortOrder(val field: SortField = SortField.DUE_DATE, val ascending: Boolean = true) {
    fun toggled(): SortOrder = copy(ascending = !ascending)
}
