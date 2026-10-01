package dev.gabrie.brainwave.data

import java.io.File
import java.text.Collator
import dev.gabrie.brainwave.util.LocaleAware
import kotlinx.coroutines.flow.Flow

class BrainwaveRepository(private val dao: BrainwaveDao) {

    fun observeAll(): Flow<List<Brainwave>> = dao.observeAll()

    fun observe(id: Long): Flow<Brainwave?> = dao.observe(id)

    suspend fun find(id: Long): Brainwave? = dao.find(id)

    suspend fun allOnce(): List<Brainwave> = dao.allOnce()

    suspend fun pendingWithDueDate(): List<Brainwave> = dao.pendingWithDueDate()

    suspend fun insert(brainwave: Brainwave): Long = dao.insert(brainwave)

    suspend fun update(brainwave: Brainwave) = dao.update(brainwave)

    suspend fun markNoteMailSent(id: Long) = dao.markNoteMailSent(id)

    suspend fun setCalendarEventId(id: Long, eventId: Long?) = dao.setCalendarEventId(id, eventId)

    suspend fun updateContent(id: Long, title: String, body: String, dueAt: Long?, dueHasTime: Boolean) =
        dao.updateContent(id, title, body, dueAt, dueHasTime)

    suspend fun setCompleted(id: Long, completed: Boolean) =
        dao.setCompleted(id, completed, if (completed) System.currentTimeMillis() else null)

    /** Deletes the row and the audio file behind it — nothing else references it. */
    suspend fun delete(brainwave: Brainwave) {
        dao.delete(brainwave)
        brainwave.audioPath?.let { path -> runCatching { File(path).delete() } }
    }

    /**
     * Re-inserts a deleted brainwave for snackbar undo. Audio is already gone,
     * and so is its calendar entry — the caller creates a fresh one.
     */
    suspend fun restore(brainwave: Brainwave): Long =
        dao.insert(brainwave.copy(id = 0L, calendarEventId = null))

    companion object {
        // Locale-aware so title sorting follows a language change; see LocaleAware.
        private val collator = LocaleAware { locale ->
            Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        }

        fun sort(items: List<Brainwave>, order: SortOrder): List<Brainwave> {
            val comparator = when (order.field) {
                // Brainwaves without a due date always sink to the bottom, in
                // both directions — an absent date is not "earlier than everything".
                SortField.DUE_DATE -> compareBy<Brainwave> { it.dueAt == null }
                    .thenComparator { a, b ->
                        val cmp = compareValues(a.dueAt, b.dueAt)
                        if (order.ascending) cmp else -cmp
                    }
                    .thenByDescending { it.createdAt }

                SortField.TITLE -> Comparator<Brainwave> { a, b ->
                    val cmp = collator.get().compare(a.title, b.title)
                    if (order.ascending) cmp else -cmp
                }
            }
            return items.sortedWith(comparator)
        }
    }
}
