# Brainwave — working notes for Claude Code

Android app: capture a thought by voice, get it emailed to yourself with the
recording attached, put on your calendar, and pushed back as a reminder.

Read this before changing anything. The rationale behind each decision, and the
alternatives that were rejected, is in [docs/decisions.md](docs/decisions.md).

## Setup on a new machine

Needs JDK 17 and an Android SDK with platform 36. Neither is guaranteed to be on
`PATH` — on the machine this was built on, Homebrew's JDK and the SDK both had to
be exported explicitly:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$PATH
```

A bare `java -version` failing does **not** mean there is no JDK. Check
`/opt/homebrew/opt/openjdk@17`, `/Library/Java/JavaVirtualMachines`, and Android
Studio's bundled JBR before concluding anything is missing.

A fresh clone builds with nothing else to fetch: the speech models are **not** in the
repository or the APK — the app downloads them on first use (see below).

## Commands

```bash
./gradlew build                  # assemble + tests + lint, both variants
```

```bash
./gradlew :app:testDebugUnitTest # the 110 unit tests
```

```bash
./gradlew :app:assembleRelease   # signed only if keystore.properties exists
```

## Hard constraints

These are the user's explicit choices, not preferences. Do not quietly trade them
away.

1. **FOSS-clean.** No Play Services, no Firebase, no analytics, no proprietary
   dependencies — the goal is an F-Droid submission later. Check any new library
   against this first. Platform APIs (`TextToSpeech`, `MediaCodec`) are fine.
2. **Everything degrades.** The rule-based `DueDateParser` and `TitleGenerator`
   always produce a result; Claude only *upgrades* it. The app must stay fully
   usable with no API key and no network. Never make the Claude path
   load-bearing.
3. **Speech runs on-device** (Vosk, models downloaded once on first use, English +
   Dutch). The cloud engine is an optional alternative and defaults to a free tier,
   never a paid one.
4. **A captured thought is never lost.** If transcription fails, the user still
   reaches the review screen with the audio saved and an editable body. Preserve
   this property in any change to the capture flow.

## Layout

```
ui/         Compose screens + ViewModels (home, record, detail, settings)
ai/         VoskTranscriber · CloudTranscriber · SpeechToTextRouter
            SpeechModelCatalog · SpeechModelManager · ModelInstaller · VoskModelStore
            DueDateParser · TitleGenerator · ClaudeClient
audio/      AudioRecorder · PcmDecoder · LinearResampler · AudioPlayer
            Speaker (TTS) · HeadsetAudioRouter
mail/       MailComposer · SmtpMailer
calendar/   CalendarWriter — writes entries via CalendarContract
work/       WorkManager jobs for the three kinds of mail
reminder/   AlarmManager scheduling, notifications, boot re-registration
data/       Room entity, DAO, repository
settings/   DataStore settings + Keystore-encrypted secrets
```

`AppContainer` is a hand-written dependency graph — no DI framework, on purpose.
`BrainwaveCoordinator` is the single place that knows what a change to a
brainwave implies (reminder rescheduled, calendar entry created/moved/removed, mail
queued); route mutations through it rather than touching the repository directly.

## Conventions

- **Test the parsers on what the speech model writes, not on what a person types.**
  `SpokenTranscriptTest` holds real Vosk output; add to it when a recording is misread.
- Pure logic gets unit tests: both date parsers, both title generators, the
  mail wire format, the resampler. Anything requiring a device does not.
- `DueDateParser` is deterministic given `(text, now, defaultHour, language)`.
  Keep it that way — it is what makes it testable.
- Adding a language = one `Phrases` table in `DueDateParser`, one entry in
  `NoteLanguage`, one entry in `SpeechModelCatalog` (with the model's SHA-256). The matching algorithm is
  language-agnostic; do not special-case a language inside it.
- The UI is English-only. Only the three spoken prompts are translated
  (`SpokenPrompts`), because those reach the user when they are not looking.

## Gotchas found the hard way

Each of these cost real time. Do not re-litigate them.

- **Vosk writes words, never digits.** "half drie", "vijftien oktober", "three thirty".
  `DueDateParser` rewrites spelled-out numbers to digits first, via `Mapped`, which
  remembers where each character came from so `ParsedDue.matchedSpans` can be slices of
  the *original* text. Do not match against the normalised text and report it back.
- **Never write a whole row back from a stale copy.** Two workers each did
  `update(brainwave.copy(flag = true))` concurrently and the slower one erased the
  other's flag — on a real phone every `inviteSent` read 0 although the mail server
  had accepted both messages. The DAO has single-purpose updates (`markNoteMailSent`,
  `setCalendarEventId`, `updateContent`, `setCompleted`); use them, and let
  `BrainwaveCoordinator` re-read the row. Writing a stale `calendarEventId` back makes
  the next edit create a duplicate calendar entry.
- **Never guess which calendar.** `CalendarWriter.resolveTarget` returns the chosen
  calendar, else the *only* writable one, else null. A phone's "primary" is routinely
  the work calendar. With several and none chosen, nothing is written until the user
  chooses; `catchUpCalendar()` fills the gap on the next start.
- **All-day calendar events are UTC midnight to UTC midnight**, with
  `EVENT_TIMEZONE = "UTC"`, whatever the phone's timezone — otherwise they drift a day.
- **Room migrations must reproduce the exported schema exactly.** Copy the `CREATE
  TABLE` from `app/schemas/.../N.json`; Room refuses to open a database that is one
  column off. SQLite below Android 14 cannot drop a column, so the table is rebuilt
  (see `Migrations.kt`). Never `fallbackToDestructiveMigration` — these are people's notes.
- **Weekday abbreviations are only trusted after a qualifier** ("on fri"). Scanned bare
  they turn "the sun is out" into a Sunday deadline and "he sat down" into a Saturday one.
  Two-letter forms are dropped entirely ("zo" is "so").
- **Day-part words decide AM/PM, and the hour *as spoken* decides the rest.** "tien voor
  acht" is 7:50 because *eight* says morning; "half drie" is 14:30 because *three* says
  afternoon. "morgenvroeg om zeven uur" must be 07:00, not 19:00.

- **`mipmap-anydpi-v26` must keep the `-v26`.** Lint says the qualifier is
  redundant at minSdk 26; removing it breaks resource linking.
- **JavaMail on Android needs `JavaMailInit.ensure()`** before the first message,
  or multipart fails at runtime with "no object DCH for MIME type".
- **Do not weaken the JavaMail ProGuard rules.** R8 will strip the reflectively
  loaded providers and handlers, and SMTP dies only in release builds.
- **`android-mail` and `android-activation` ship duplicate `META-INF` licence
  files** in both `.txt` and `.md`; the packaging `excludes` glob covers them.
- **`BLUETOOTH_CONNECT` is a runtime permission.** Declaring it is not enough —
  unrequested, Bluetooth SCO never engages and the headset mic is silently unused.
- **Locale-derived values must not be cached in statics.** Collators, formatters
  and month tables go stale when the user changes language; use `LocaleAware`.
- **Vosk needs 16 kHz mono PCM.** The recorder writes AAC so mail attachments stay
  small, hence `PcmDecoder` + `LinearResampler`. Feeding the wrong sample rate
  produces confident nonsense, not an error.
- **Speech models are downloaded, never bundled, and trusted only by checksum.**
  `SpeechModelCatalog` pins each model's URL, size and SHA-256; `SpeechModelManager`
  downloads, `ModelInstaller` verifies *before* unpacking, refuses path-traversal
  ("zip-slip") entries, caps unpacked size, and swaps a new model in only once it is
  complete. They were bundled once (a 90 MB APK); that was reversed for F-Droid, which
  builds from source and takes no large blobs. Do not bundle them again, and do not
  loosen the installer without a test — `ModelInstallerTest` covers the attacks.
- **Existing model folders are reused.** The folder names are the ones the bundled build
  used (`filesDir/vosk/en-us`, `.../nl`); a folder with no `.sha256` marker is accepted
  as current, so phones upgraded from a bundled build do not download again.
- **Vosk's own `StorageService` unpacks to external storage** behind a magic `uuid` file
  and cannot verify anything; it is not used.
- **Compose BOM and material3 must stay in step.** `2025.09.01` is the first BOM
  carrying material3 1.4.x (Expressive) with a matching `compose-ui`.
- **Kotlin:** a function reference cannot adapt to `() -> Unit` if it returns
  something else. ViewModel actions return `Unit`, not `Job`, for this reason.
  Method references also cannot fill in default parameters.

## Do not commit

`keystore.properties`, the `.jks` itself and `local.properties`. All are gitignored;
keep it that way.
