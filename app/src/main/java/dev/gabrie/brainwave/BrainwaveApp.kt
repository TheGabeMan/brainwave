package dev.gabrie.brainwave

import android.app.Application
import dev.gabrie.brainwave.mail.JavaMailInit

class BrainwaveApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderScheduler.ensureChannel()
        // Registering the JavaMail content handlers is cheap and has to happen
        // before the first MIME message is assembled, on whichever thread.
        JavaMailInit.ensure()
    }
}
