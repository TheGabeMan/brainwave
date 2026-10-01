package dev.gabrie.brainwave.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.brainwaveApp
import dev.gabrie.brainwave.mail.MailComposer
import dev.gabrie.brainwave.mail.SmtpMailer
import dev.gabrie.brainwave.settings.SecretStore

/**
 * Shared plumbing for the mail jobs.
 *
 * Sending happens in WorkManager rather than inline so a brainwave captured in
 * a lift or on aeroplane mode still goes out later — the recording is saved
 * locally the moment it is captured, and delivery catches up.
 */
abstract class MailWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    protected val container: AppContainer get() = applicationContext.brainwaveApp().container

    protected suspend fun sendOrRetry(block: suspend () -> Unit): Result = try {
        block()
        Result.success()
    } catch (e: SmtpMailer.MailException) {
        Log.w(TAG, "Mail send failed (attempt $runAttemptCount)", e)
        val reason = e.message ?: "Unknown SMTP error"
        if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            // Silent failure is the worst outcome here — the user believes the
            // mail went out. Tell them once retries are exhausted.
            MailFailureNotifier(applicationContext).notify(failureTitle(), reason)
            Result.failure(workDataOf(KEY_ERROR to reason))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Mail job failed permanently", e)
        val reason = e.message ?: e::class.java.simpleName
        MailFailureNotifier(applicationContext).notify(failureTitle(), reason)
        Result.failure(workDataOf(KEY_ERROR to reason))
    }

    protected abstract fun failureTitle(): String

    companion object {
        const val KEY_BRAINWAVE_ID = "brainwave_id"
        const val KEY_ERROR = "error"
        const val MAX_ATTEMPTS = 4
        private const val TAG = "MailWorker"
    }
}

/** Sends the "[brainwave] <title>" mail with the recording attached. */
class SendNoteWorker(context: Context, params: WorkerParameters) : MailWorker(context, params) {

    override fun failureTitle() = "Couldn't email your brainwave"

    override suspend fun doWork(): Result = sendOrRetry {
        val id = inputData.getLong(KEY_BRAINWAVE_ID, -1L)
        val brainwave = container.repository.find(id) ?: return@sendOrRetry
        val settings = container.settingsRepository.current()
        val password = container.secretStore.get(SecretStore.SMTP_PASSWORD)

        container.mailer.send(MailComposer.note(brainwave, settings), settings, password)
        // Single-column write: see BrainwaveDao.markNoteMailSent for why not update(copy()).
        container.repository.markNoteMailSent(id)
    }
}

/** Sends the whole list as a single mail, on demand. */
class SendListWorker(context: Context, params: WorkerParameters) : MailWorker(context, params) {

    override fun failureTitle() = "Couldn't email your brainwave list"

    override suspend fun doWork(): Result = sendOrRetry {
        val settings = container.settingsRepository.current()
        val password = container.secretStore.get(SecretStore.SMTP_PASSWORD)
        val all = container.repository.allOnce()

        container.mailer.send(MailComposer.list(all), settings, password)
    }
}
