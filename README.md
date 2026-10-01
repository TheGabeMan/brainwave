# Brainwave

An Android app for catching a thought by voice, and making sure it comes back to
you: emailed to yourself with the recording attached, put on your calendar, and
pushed at you as a reminder when it is due.

Kotlin, Jetpack Compose, Material 3 (dynamic colour + expressive shape/motion).
No Google Play Services, no Firebase, no analytics — the dependency list is
FOSS-clean so an F-Droid submission later is packaging work, not a rewrite.

---

## What it does

| | |
|---|---|
| **Home screen** | Top half is one enormous record button. Bottom half is your list, showing each title with the due date underneath. |
| **Sorting** | By due date or by title, ascending or descending. Tapping the field you are already sorting by flips the direction. Undated brainwaves always sink to the bottom. |
| **Capture** | Recording starts the moment the screen opens. Speech-to-text runs **on the phone** (Vosk, no account and no internet once its model is downloaded), then a title is generated and a due date extracted from what you said. |
| **Languages** | English and Dutch, picked in Settings. One setting drives three things: which speech model loads, which phrasings the date parser looks for, and which voice answers you. |
| **Missing due date** | The app asks *"When is this due?"* / *"Wanneer moet dit af zijn?"* out loud and immediately listens for the answer, then parses it. Skippable, and there is a date picker on the review screen either way. |
| **Typing instead** | "Type instead" on the recording screen goes straight to a text editor. |
| **Email** | On save: subject `[brainwave] <title>`, body with the text and due date, the `.m4a` recording attached. |
| **Calendar** | Each brainwave with a due date is written **straight into the phone's calendar** — no email involved. Timed entries get a reminder; day-only ones are all-day events. Editing the brainwave updates the entry, completing or deleting it removes it. If the phone has several calendars the app asks which once and never guesses (see *Calendar* below). |
| **Whole list** | The inbox icon in the top bar emails your entire list as one message, plain text and HTML. |
| **Swipes** | Swipe **left** to complete. Swipe **right** to delete, with an undo snackbar. |
| **Editing** | Tap a brainwave to edit title, text and due date, play the recording back, or re-send the email. |
| **Reminders** | A local notification at the due moment (or N minutes before), rescheduled after a reboot. |
| **Headsets** | Wired, USB and Bluetooth. Bluetooth SCO is engaged explicitly so the headset's own microphone is used and the spoken prompt goes to your ear, not the loudspeaker. |

## Architecture

```
ui/            Compose screens + ViewModels (home, record, detail, settings)
ai/            VoskTranscriber · CloudTranscriber · SpeechToTextRouter
               DueDateParser · TitleGenerator · ClaudeClient
audio/         AudioRecorder · PcmDecoder · LinearResampler · AudioPlayer
               Speaker (TTS) · HeadsetAudioRouter
mail/          MailComposer · SmtpMailer
calendar/      CalendarWriter (CalendarContract — a platform API, no Google libraries)
work/          WorkManager jobs for the three kinds of mail
reminder/      AlarmManager scheduling, notification, boot re-registration
data/          Room entity, DAO, repository
settings/      DataStore settings + Keystore-encrypted secrets
BrainwaveCoordinator   the one place that knows what a change implies
```

Dependencies are wired by hand in `AppContainer` — the graph is small enough
that an annotation processor would cost more than it saves.

### Two design decisions worth knowing

**Everything degrades.** The rule-based `DueDateParser` and `TitleGenerator` run
first and always produce a result. Claude, when enabled, only *upgrades* that
result. If transcription itself fails, you still land on the review screen with
the audio saved and an empty body you can type into. A captured thought is never
lost to a network error.

**Mail goes through WorkManager,** not inline, so a brainwave captured in a lift
or on a plane still goes out when the phone reconnects. If all retries fail you
get a notification — delivery never fails silently.

### Why there is a PCM decoder

Vosk wants raw 16 kHz mono PCM; the recorder writes AAC in an MP4 container so
the email attachment stays around 240 KB a minute instead of 2 MB. `PcmDecoder`
bridges the two with the platform codec, and `LinearResampler` covers devices
whose AAC encoder quietly ignores the requested 16 kHz — feeding a Vosk model
the wrong sample rate produces confident nonsense rather than an error.

## Setup

### Build

The Gradle wrapper is committed, so a build needs only JDK 17 and an Android SDK
with platform 36 — nothing to fetch first:

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

The unit tests cover the English and Dutch date parsers (including a suite built
from real speech-model output, see below), both title generators, the mail wire
format, the streaming resampler, and the speech-model download and install path
— the parts most worth pinning down.

**Verified green** with AGP 8.10.1, Kotlin 2.1.21, Gradle 8.11.1, JDK 17,
compileSdk 36: `assembleDebug`, `assembleRelease` (R8 minified), `build` (both
variants) and `lintDebug` (no errors; the remaining warnings are all "a newer
version of X is available").

All versions live in one place: `gradle/libs.versions.toml`. The Compose BOM is
pinned to `2025.09.01` because that is the first BOM carrying material3 1.4.x
(Material 3 Expressive) with a matching `compose-ui`; if you bump it, keep those
two in step.

### Continuing on another machine

Everything needed is in the repository:

```bash
git clone https://github.com/TheGabeMan/brainwave.git
```

Then open it in Android Studio, or build from the CLI with JDK 17 and an Android
SDK on `ANDROID_HOME`.

If you use Claude Code, it reads [CLAUDE.md](CLAUDE.md) automatically at session
start — the constraints, conventions and the list of things that have already
bitten us travel with the repo. The reasoning behind each choice is in
[docs/decisions.md](docs/decisions.md). `.claude/settings.json` pre-approves the
build and read-only inspection commands so you are not prompted for every
`./gradlew`.

**What does not travel:** the release keystore and its `keystore.properties`.
Both are gitignored on purpose. Copy them across by hand (or from a backup) if
you want to publish updates your family can install over their existing app —
sign with a different key and they have to uninstall first.

Nothing else is machine-specific. The app's own secrets — SMTP password, API
keys — live encrypted on each phone, not in the project.

### Calendar

Entries are written with `CalendarContract`, so they sync wherever that calendar's
account lives (Google, Nextcloud via DAVx⁵, …). The first time a brainwave with a
due date is saved the app asks for calendar permission and — if there is more than
one writable calendar — which to use. **It never picks one for you:** "primary" on a
phone with a work calendar, a family calendar and a personal one is whichever the
account was set up with, and a shopping reminder in the wrong one is worse than none.
Change the choice in Settings → Calendar; doing so moves existing entries across.

This replaced an emailed `.ics` invite. On a real phone the mail server accepted every
invite and none ever became a calendar entry, and nothing in the app could see why.

### What the speech model actually writes

Vosk never emits digits. It writes "half drie", "vijftien oktober", "three thirty",
"the fifteenth of october". The date parser therefore rewrites spelled-out numbers into
digits first (keeping a map back to the original text, so the title generator can still
strip the date phrase out), and `SpokenTranscriptTest` is built from **real recognizer
output** — a parser tested only on typed input ("14:30", "15 october") passes every
test and still fails on a phone. Recognition itself is not perfect: the small models
mishear things ("overmorgen" → "vanmorgen") that no parser can repair.

### Configure (Settings screen)

1. **Recipient email** — where everything is sent.
2. **SMTP** — host, security, port, username, password. With Gmail use
   `smtp.gmail.com`, STARTTLS on 587, and an **app password**, not your account
   password. Hit *Send test email* before trusting it.
3. **Spoken language** — English or Dutch. Speech recognition runs on the phone,
   free and offline, from a model the app downloads **once** (about 40 MB per
   language, SHA-256-verified; it asks before downloading, and your recording is
   kept either way).
   Optionally switch the engine to **Server** to use any OpenAI-compatible
   `/v1/audio/transcriptions` endpoint instead — Groq has a free tier, and a
   self-hosted `whisper.cpp` or `faster-whisper` box needs no key at all.
4. **Claude** *(optional)* — switch on and add an Anthropic API key for better
   titles and for dates the regexes cannot reach ("the Thursday after the school
   holidays"). Defaults to `claude-opus-5`. Off by default; off means fully
   offline analysis.

Secrets are sealed with an AES-GCM key generated inside the Android Keystore, so
the key material never enters the app process and a copied preferences file is
useless on another device. That is also why backups deliberately exclude them —
you re-enter passwords once after a restore.

### APK size

The release APK is **~12 MB**: `libvosk.so` (10 MB, arm64 only) and the app itself.
The speech models are not inside it. They are downloaded on first use from
`alphacephei.com`, checked against a SHA-256 pinned in `SpeechModelCatalog`, and
unpacked into the app's private storage.

They used to be bundled (a 90 MB APK that worked with no setup at all). That was
changed to publish on F-Droid, which builds from source and does not accept large
binary blobs; fetching the model on first run is how other Vosk apps on F-Droid
do it. See `docs/decisions.md`.

Only arm64 is built (see `abiFilters`); every Android 8 phone is arm64, and
including 32-bit ARM added 9 MB.

### Distribute to family

Family members install from a plain download link — no store, no extra app. The step-by-step
guide, in English and Dutch, is [docs/INSTALL.md](docs/INSTALL.md); send them its link:

> <https://github.com/TheGabeMan/brainwave/releases/latest/download/brainwave.apk>

That URL always serves the newest release, because every release uploads its APK under the
same file name, `brainwave.apk`.

**Releasing a new version**

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build it signed: `./gradlew :app:assembleRelease` (it signs automatically when
   `keystore.properties` exists in the repo root; the file is git-ignored — see below).
3. **Run the release APK on a device.** Debug and release are different builds (release is
   minified), and a release that has only ever compiled has not been tested.
4. Tag the commit `vX.Y.Z`, push the tag, and create a GitHub release for it with the APK
   attached **as `brainwave.apk`**.

`keystore.properties`, in the repo root, is git-ignored:

```properties
storeFile=/absolute/path/to/brainwave-release.jks
storePassword=…
keyAlias=brainwave
keyPassword=…
```

**Keep the keystore somewhere safe and backed up, outside the repository.** Every install is
verified against it: lose it, or sign a release with a different one, and family members must
uninstall before they can update — and lose their brainwaves, which live on the phone.

### F-Droid

Ready on the code side: MIT licensed, no proprietary dependencies, no Play
Services, `dependenciesInfo` stripped from the APK, no binary blobs in the source
tree, store text in `fastlane/metadata/`, and a draft submission recipe in
[`fdroid/dev.gabrie.brainwave.yml`](fdroid/dev.gabrie.brainwave.yml).

What publishing needs, none of which can be done from inside this repository:

1. The public source: <https://github.com/TheGabeMan/brainwave>.
2. A release tag matching `versionName` (`v1.0.0`).
3. A merge request to [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) adding the
   recipe — see [`fdroid/README.md`](fdroid/README.md).

F-Droid signs the app with **its own key**, so anyone who installed a build signed
with yours must uninstall once before updating from F-Droid. Decide the channel
before handing release APKs to family.

## Known gaps

- **Exercised on one phone only.** Used day to day on a single Pixel: recording,
  on-device transcription, the review screen, SMTP mail, reminders and calendar
  entries. The Bluetooth headset path, the optional cloud engine and the Claude
  enrichment have not been exercised on real hardware.
- **No instrumented or UI tests.** The tested surface is the pure-logic core.
- **The interface is English**, even when the spoken language is Dutch. Only the
  three spoken prompts are translated, on the grounds that those are the ones
  that reach you when you are not looking at the screen. Localising the UI is a
  separate job.
- **Two languages, not N.** Adding a third means one entry in `SpeechModelCatalog`
  (with the model's pinned SHA-256) and one more `Phrases` table in `DueDateParser` — the matching algorithm
  itself is language-agnostic.
- **On-device recognition writes lower case with no punctuation.** Titles read
  flatter than a cloud model would give you, and the "first sentence" heuristic
  rarely fires, so the length cap does the work. Titles are editable on the
  review screen, and enabling Claude fixes it properly.
- **The Claude call uses raw HTTP over OkHttp** rather than the Anthropic Java
  SDK. The SDK targets JVM servers and pulls a heavy dependency graph onto
  Android for what is one small request, and OkHttp was already present for
  transcription.
- **No recording-in-background.** Recording stops if you leave the screen — fine
  for capture-sized notes. (`FOREGROUND_SERVICE` and `WAKE_LOCK` do appear in
  the merged manifest; those are WorkManager's, for delivering mail, not the
  recorder's.)
