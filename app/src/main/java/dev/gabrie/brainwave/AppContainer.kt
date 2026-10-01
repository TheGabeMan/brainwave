package dev.gabrie.brainwave

import android.content.Context
import dev.gabrie.brainwave.ai.BrainwaveAnalyzer
import dev.gabrie.brainwave.ai.CloudTranscriber
import dev.gabrie.brainwave.ai.SpeechModelManager
import dev.gabrie.brainwave.ai.SpeechToText
import dev.gabrie.brainwave.ai.SpeechToTextRouter
import dev.gabrie.brainwave.ai.VoskModelStore
import dev.gabrie.brainwave.ai.VoskTranscriber
import dev.gabrie.brainwave.calendar.CalendarWriter
import dev.gabrie.brainwave.data.BrainwaveDatabase
import dev.gabrie.brainwave.data.BrainwaveRepository
import dev.gabrie.brainwave.mail.SmtpMailer
import dev.gabrie.brainwave.reminder.ReminderScheduler
import dev.gabrie.brainwave.settings.SecretStore
import dev.gabrie.brainwave.settings.SettingsRepository
import dev.gabrie.brainwave.work.MailWorkScheduler
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Manual dependency graph.
 *
 * The app has one screenful of collaborators and no test doubles to inject at
 * runtime, so a lazily-built container is cheaper — in build time and in
 * reader effort — than an annotation-processed DI framework.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database by lazy { BrainwaveDatabase.build(appContext) }
    val repository by lazy { BrainwaveRepository(database.brainwaveDao()) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val secretStore by lazy { SecretStore(appContext) }

    /** Outlives any screen, so a model download carries on while the user moves around. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val speechModels by lazy {
        SpeechModelManager(
            root = File(appContext.filesDir, "vosk"),
            scratch = appContext.cacheDir,
            http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build(),
            scope = appScope,
        )
    }
    val voskModels by lazy { VoskModelStore(speechModels) }
    val transcription: SpeechToText by lazy {
        SpeechToTextRouter(onDevice = VoskTranscriber(voskModels), cloud = CloudTranscriber())
    }
    val analyzer by lazy { BrainwaveAnalyzer() }
    val mailer by lazy { SmtpMailer() }

    val reminderScheduler by lazy { ReminderScheduler(appContext) }
    val mailWorkScheduler by lazy { MailWorkScheduler(appContext) }
    val calendarWriter by lazy { CalendarWriter(appContext) }

    val coordinator by lazy {
        BrainwaveCoordinator(repository, settingsRepository, reminderScheduler, mailWorkScheduler, calendarWriter)
    }
}

fun Context.brainwaveApp(): BrainwaveApp = applicationContext as BrainwaveApp
