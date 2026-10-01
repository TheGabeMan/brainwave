package dev.gabrie.brainwave.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class MailWorkScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun sendNote(brainwaveId: Long) = enqueue<SendNoteWorker>("note-$brainwaveId", brainwaveId)

    fun sendList() {
        val request = OneTimeWorkRequestBuilder<SendListWorker>()
            .setConstraints(CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("list-export", ExistingWorkPolicy.REPLACE, request)
    }

    private inline fun <reified W : MailWorker> enqueue(name: String, brainwaveId: Long) {
        val request = OneTimeWorkRequestBuilder<W>()
            .setInputData(workDataOf(MailWorker.KEY_BRAINWAVE_ID to brainwaveId))
            .setConstraints(CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE, not KEEP: editing a brainwave should re-send the current
        // version rather than let a queued copy of the old one win.
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        val CONSTRAINTS: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
