package dev.gabrie.brainwave.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainwaveDao {

    /**
     * Sorting is applied in memory (see [BrainwaveRepository]) because title
     * ordering has to be locale-aware, which SQLite's BINARY collation is not.
     */
    @Query("SELECT * FROM brainwaves ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Brainwave>>

    @Query("SELECT * FROM brainwaves WHERE id = :id")
    fun observe(id: Long): Flow<Brainwave?>

    @Query("SELECT * FROM brainwaves WHERE id = :id")
    suspend fun find(id: Long): Brainwave?

    @Query("SELECT * FROM brainwaves WHERE completed = 0 AND dueAt IS NOT NULL")
    suspend fun pendingWithDueDate(): List<Brainwave>

    @Query("SELECT * FROM brainwaves ORDER BY createdAt DESC")
    suspend fun allOnce(): List<Brainwave>

    @Insert
    suspend fun insert(brainwave: Brainwave): Long

    @Update
    suspend fun update(brainwave: Brainwave)

    @Delete
    suspend fun delete(brainwave: Brainwave)

    /**
     * Single-column updates, deliberately not `update(brainwave.copy(...))`.
     *
     * A worker that reads a row, changes one flag and writes the whole row back
     * silently undoes whatever another writer changed in between. That is not
     * hypothetical: the note-mail job and the (former) invite job ran
     * concurrently, and the slower one overwrote the other's flag — which is why
     * every brainwave on the test phone showed its invite as unsent even though
     * the mail server had accepted both messages.
     */
    @Query(
        "UPDATE brainwaves SET title = :title, body = :body, dueAt = :dueAt, dueHasTime = :dueHasTime WHERE id = :id"
    )
    suspend fun updateContent(id: Long, title: String, body: String, dueAt: Long?, dueHasTime: Boolean)

    @Query("UPDATE brainwaves SET completed = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?)

    @Query("UPDATE brainwaves SET noteMailSent = 1 WHERE id = :id")
    suspend fun markNoteMailSent(id: Long)

    @Query("UPDATE brainwaves SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: Long, eventId: Long?)
}
