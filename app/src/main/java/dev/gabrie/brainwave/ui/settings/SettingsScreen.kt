package dev.gabrie.brainwave.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import dev.gabrie.brainwave.ai.SpeechModelCatalog
import dev.gabrie.brainwave.ai.ModelStatus
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gabrie.brainwave.settings.NoteLanguage
import dev.gabrie.brainwave.ui.components.rememberCalendarActions
import dev.gabrie.brainwave.settings.SmtpSecurity
import dev.gabrie.brainwave.settings.SpeechEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val modelStatuses by viewModel.modelStatuses.collectAsStateWithLifecycle()
    val settings = form.settings
    val snackbarHostState = remember { SnackbarHostState() }
    val calendar = rememberCalendarActions()

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        calendar.Dialogs()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Where everything goes")
            OutlinedTextField(
                value = settings.recipientEmail,
                onValueChange = { value -> viewModel.edit { it.copy(recipientEmail = value) } },
                label = { Text("Recipient email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionHeader("Outgoing mail (SMTP)")
            Text(
                text = "With Gmail, use smtp.gmail.com and an app password — not your normal password.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = settings.smtpHost,
                onValueChange = { value -> viewModel.edit { it.copy(smtpHost = value) } },
                label = { Text("SMTP host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SmtpSecurity.entries.forEachIndexed { index, security ->
                    SegmentedButton(
                        selected = settings.smtpSecurity == security,
                        onClick = { viewModel.setSmtpSecurity(security) },
                        shape = SegmentedButtonDefaults.itemShape(index, SmtpSecurity.entries.size),
                        label = { Text(security.label()) },
                    )
                }
            }

            OutlinedTextField(
                value = settings.smtpPort.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { port -> viewModel.edit { it.copy(smtpPort = port) } }
                },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = settings.smtpUsername,
                onValueChange = { value -> viewModel.edit { it.copy(smtpUsername = value) } },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SecretField(
                value = form.smtpPassword,
                onValueChange = viewModel::setSmtpPassword,
                label = "Password or app password",
            )

            OutlinedTextField(
                value = settings.fromAddress,
                onValueChange = { value -> viewModel.edit { it.copy(fromAddress = value) } },
                label = { Text("From address (defaults to the username)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::sendTestEmail,
                enabled = !form.testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (form.testing) "Sending…" else "Send test email")
            }

            HorizontalDivider()
            SectionHeader("Spoken language")
            Text(
                text = "Sets the speech model, the words the app listens for when reading " +
                    "dates out of your note, and the voice it answers in.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                NoteLanguage.entries.forEachIndexed { index, language ->
                    SegmentedButton(
                        selected = settings.noteLanguage == language,
                        onClick = { viewModel.edit { it.copy(noteLanguage = language) } },
                        shape = SegmentedButtonDefaults.itemShape(index, NoteLanguage.entries.size),
                        label = { Text(language.displayName) },
                    )
                }
            }

            HorizontalDivider()
            SectionHeader("Speech to text")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.speechEngine == SpeechEngine.ON_DEVICE,
                    onClick = { viewModel.edit { it.copy(speechEngine = SpeechEngine.ON_DEVICE) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text("On this phone") },
                )
                SegmentedButton(
                    selected = settings.speechEngine == SpeechEngine.CLOUD,
                    onClick = { viewModel.edit { it.copy(speechEngine = SpeechEngine.CLOUD) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text("Server") },
                )
            }

            if (settings.speechEngine == SpeechEngine.ON_DEVICE) {
                Text(
                    text = "Recognition runs offline with a Vosk model that is downloaded once. " +
                        "Free, no account, and the audio never leaves your phone. It writes in " +
                        "lower case without punctuation, so titles read a little flatter.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SpeechModelRow(
                    language = settings.noteLanguage,
                    status = modelStatuses[settings.noteLanguage] ?: ModelStatus.NotInstalled,
                    onDownload = { viewModel.downloadModel(settings.noteLanguage) },
                    onCancel = { viewModel.cancelModelDownload(settings.noteLanguage) },
                )
            } else {
                Text(
                    text = "Any OpenAI-compatible /audio/transcriptions endpoint. Groq has a " +
                        "free tier; a whisper.cpp server on your own machine needs no key at all.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = settings.transcriptionEndpoint,
                    onValueChange = { value -> viewModel.edit { it.copy(transcriptionEndpoint = value) } },
                    label = { Text("Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.transcriptionModel,
                    onValueChange = { value -> viewModel.edit { it.copy(transcriptionModel = value) } },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(
                    value = form.transcriptionKey,
                    onValueChange = viewModel::setTranscriptionKey,
                    label = "API key (leave empty for a local server)",
                )
            }

            HorizontalDivider()
            SectionHeader("Smarter titles and dates")
            ToggleRow(
                label = "Use Claude to read the note",
                description = "Off: titles and dates come from on-device rules, no network needed.",
                checked = settings.useClaude,
                onCheckedChange = { value -> viewModel.edit { it.copy(useClaude = value) } },
            )
            if (settings.useClaude) {
                OutlinedTextField(
                    value = settings.claudeModel,
                    onValueChange = { value -> viewModel.edit { it.copy(claudeModel = value) } },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(
                    value = form.claudeKey,
                    onValueChange = viewModel::setClaudeKey,
                    label = "Anthropic API key",
                )
            }

            HorizontalDivider()
            SectionHeader("Calendar")
            ToggleRow(
                label = "Add due dates to my calendar",
                description = form.calendarStatus.ifBlank { null },
                checked = settings.calendarEnabled,
                onCheckedChange = { on -> calendar.ensure(on) { viewModel.setCalendarEnabled(on) } },
            )
            if (settings.calendarEnabled) {
                OutlinedButton(
                    onClick = { calendar.choose { viewModel.refreshCalendar() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choose calendar") }
            }

            HorizontalDivider()
            SectionHeader("Reminders")
            ToggleRow(
                label = "Notify me when a brainwave is due",
                description = null,
                checked = settings.remindersEnabled,
                onCheckedChange = { value -> viewModel.edit { it.copy(remindersEnabled = value) } },
            )
            OutlinedTextField(
                value = settings.reminderLeadMinutes.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { minutes ->
                        viewModel.edit { it.copy(reminderLeadMinutes = minutes.coerceIn(0, 10_080)) }
                    }
                },
                label = { Text("Minutes before the due time") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.defaultDueHour.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { hour ->
                        viewModel.edit { it.copy(defaultDueHour = hour.coerceIn(0, 23)) }
                    }
                },
                label = { Text("Default hour for all-day brainwaves") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(
                label = "Ask for a missing due date out loud",
                description = "Uses your phone's text-to-speech, and your headset when one is connected.",
                checked = settings.speakPrompts,
                onCheckedChange = { value -> viewModel.edit { it.copy(speakPrompts = value) } },
            )

            HorizontalDivider()
            Text(
                text = "Brainwave stores everything on this phone. With on-device speech the " +
                    "only network calls are your SMTP server, and Claude if you switch it on.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun SmtpSecurity.label(): String = when (this) {
    SmtpSecurity.STARTTLS -> "STARTTLS"
    SmtpSecurity.SSL -> "SSL/TLS"
    SmtpSecurity.NONE -> "None"
}


/** Shows whether the chosen language's speech model is on the phone, and fetches it. */
@Composable
private fun SpeechModelRow(
    language: NoteLanguage,
    status: ModelStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val sizeMegabytes = SpeechModelCatalog.forLanguage(language).sizeMegabytes

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        when (status) {
            ModelStatus.Ready -> Text(
                text = "${language.displayName} speech model installed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            ModelStatus.NotInstalled -> {
                Text(
                    text = "The ${language.displayName} speech model is not installed " +
                        "(about $sizeMegabytes MB, downloaded once — Wi-Fi recommended).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download") }
            }

            is ModelStatus.Downloading -> {
                LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Downloading… ${(status.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }

            is ModelStatus.Failed -> {
                Text(
                    text = status.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            }
        }
    }
}
