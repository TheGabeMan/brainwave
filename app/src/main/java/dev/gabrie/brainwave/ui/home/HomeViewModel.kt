package dev.gabrie.brainwave.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gabrie.brainwave.AppContainer
import dev.gabrie.brainwave.data.Brainwave
import dev.gabrie.brainwave.data.BrainwaveRepository
import dev.gabrie.brainwave.data.SortField
import dev.gabrie.brainwave.mail.MailHandoff
import dev.gabrie.brainwave.settings.MailMethod
import dev.gabrie.brainwave.data.SortOrder
import dev.gabrie.brainwave.ui.container
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val brainwaves: List<Brainwave> = emptyList(),
    val sortOrder: SortOrder = SortOrder(),
    val showCompleted: Boolean = false,
    val canEmail: Boolean = false,
    val loaded: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** Mail-app hand-offs; only a screen can open another app, so the screen launches them. */
    private val handoffs = Channel<MailHandoff.Request>(Channel.BUFFERED)
    val handoffFlow = handoffs.receiveAsFlow()

    /** Holds the last swipe-deleted brainwave so the snackbar can undo it. */
    private val recentlyDeleted = MutableStateFlow<Brainwave?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        container.repository.observeAll(),
        container.settingsRepository.settings,
    ) { items, settings ->
        val visible = if (settings.showCompleted) items else items.filterNot { it.completed }
        HomeUiState(
            brainwaves = BrainwaveRepository.sort(visible, settings.sortOrder),
            sortOrder = settings.sortOrder,
            showCompleted = settings.showCompleted,
            canEmail = settings.canEmail,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setSortField(field: SortField) {
        viewModelScope.launch {
            container.settingsRepository.update { current ->
                // Tapping the field you are already sorting by flips the direction —
                // the same gesture people expect from a table header.
                if (current.sortField == field) {
                    current.copy(sortAscending = !current.sortAscending)
                } else {
                    current.copy(sortField = field, sortAscending = true)
                }
            }
        }
    }

    fun toggleDirection() {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(sortAscending = !it.sortAscending) }
        }
    }

    fun setShowCompleted(show: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(showCompleted = show) }
        }
    }

    fun complete(brainwave: Brainwave) {
        viewModelScope.launch {
            container.coordinator.setCompleted(brainwave.id, !brainwave.completed)
            messages.send(if (brainwave.completed) "Marked as open" else "Completed: ${brainwave.title}")
        }
    }

    fun delete(brainwave: Brainwave) {
        viewModelScope.launch {
            recentlyDeleted.value = brainwave
            container.coordinator.delete(brainwave)
            messages.send(DELETED_PREFIX + brainwave.title)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            val brainwave = recentlyDeleted.value ?: return@launch
            recentlyDeleted.value = null
            // The audio file was removed with the row, so the restored brainwave
            // keeps its text but loses the recording.
            container.coordinator.restore(brainwave.copy(audioPath = null))
        }
    }

    fun emailWholeList() {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            when {
                settings.sendsAutomatically -> {
                    container.coordinator.sendWholeList()
                    messages.send("Sending your brainwave list to ${settings.recipientEmail}")
                }
                settings.handsOffToMailApp ->
                    container.coordinator.listHandoff()?.let { handoffs.send(it) }
                settings.mailMethod == MailMethod.MAIL_APP ->
                    messages.send("Add a recipient email in Settings first.")
                else -> messages.send("Add SMTP details and a recipient in Settings first.")
            }
        }
    }

    companion object {
        const val DELETED_PREFIX = "Deleted: "

        val Factory = viewModelFactory {
            initializer { HomeViewModel(this.container) }
        }
    }
}
