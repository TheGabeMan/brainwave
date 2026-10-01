# Decision record

Why the project is built the way it is, including the options that were
considered and dropped. Entries marked **user choice** were decided explicitly by
the project owner and should not be reversed without asking.

---

## 1. Speech-to-text runs on the device — **user choice**

*Decided 2026-08-21, revised 2026-08-22. Delivery of the models revised 2026-10-01 — see decision 17.*

**Decision.** Vosk, on-device. (The models were bundled in the APK at first; they are now
downloaded on first use — decision 17.) An OpenAI-compatible HTTP engine remains
selectable in Settings as an alternative, defaulting to a free tier.

**Why.** The original choice was a paid cloud API. It was revised with the
requirement "free cloud alternative or local". On-device wins on cost (free
forever), privacy (audio never leaves the phone), offline capability, and
F-Droid cleanliness — no key to distribute to family members.

**Rejected.**
- *Android's `SpeechRecognizer`* — free and accurate, but backed by a
  proprietary Google service, which contradicts decision 5.
- *Cloud-only* — needs an API key or a home server per family member.
- *Model downloaded on first run* — rejected at first for the failure mode it adds
  on first use, and **adopted later** when F-Droid made it the right trade (decision 17).

**Consequences.** Vosk emits lower-case text with no punctuation, so titles read
flatter than a cloud model's would. (The APK size consequence this entry once
described is superseded by decision 17.)

---

## 2. English and Dutch — **user choice**

*Decided 2026-08-22.*

**Decision.** Two spoken languages, selected by one setting that simultaneously
picks the Vosk model, the date-parser phrase table, and the TTS voice.

**Why.** One setting rather than three because they cannot meaningfully diverge —
a Dutch note parsed with English phrasings would silently fail to find dates and
would prompt out loud every single time.

**Consequences.** Each additional language costs a ~40 MB model plus a `Phrases`
table. The parsing algorithm itself is language-agnostic, so the marginal cost is
data, not logic. The interface stays English; only spoken prompts are translated,
on the grounds that those reach the user when they are not looking at the screen.

---

## 3. Offline rules first, Claude as an optional upgrade — **user choice**

*Decided 2026-08-21.*

**Decision.** `DueDateParser` and `TitleGenerator` always run and always produce
a result. If a Claude API key is configured, its answer supersedes theirs.

**Why.** The app must work with no key and no network. Making the LLM
load-bearing would turn a flaky connection into a broken app.

**Consequences.** This is a hard invariant. `BrainwaveAnalyzer` falls back to the
offline result on any exception, and treats a Claude refusal (`stop_reason:
"refusal"`) as simply "no answer" rather than an error. If Claude finds no date
but the rules did, the rules' date is kept.

---

## 4. Mail is sent directly over SMTP — **user choice**

*Decided 2026-08-21.*

**Decision.** The app sends mail itself using SMTP credentials entered in
Settings, rather than handing off to a mail app.

**Why.** The requirement was that capturing a brainwave sends the mail. A share
sheet would mean a tap per brainwave.

**Rejected.** *Share intent* — no credentials to store, but not automatic.

**Consequences.** A password must be stored on the device; see decision 10. SMTP
carries the note and the whole-list mail only — calendar entries do not travel by
mail (decision 13). Mail runs through WorkManager so a note captured underground still goes out later, and
a job that exhausts its retries raises a notification rather than failing
silently.

---

## 5. FOSS-clean from day one — **user choice**

*Decided 2026-08-21.*

**Decision.** No Play Services, no Firebase, no analytics, no proprietary
dependencies, MIT licensed, `dependenciesInfo` stripped from the APK, F-Droid
metadata present.

**Why.** An F-Droid submission is a stated goal; retrofitting FOSS purity later
usually means replacing whichever library you built on.

**Consequences.** This constrains library choice more than anything else in this
document. Verified by inspecting the merged release manifest: no Play Services or
advertising permissions. Platform APIs such as `TextToSpeech` and `MediaCodec`
are system services, not bundled libraries, and remain acceptable.

---

## 6. Claude is called over raw HTTP, not the Anthropic Java SDK

*Decided 2026-08-21.*

**Decision.** `ClaudeClient` builds the Messages API request with OkHttp.

**Why.** The official SDK targets JVM servers and pulls a large dependency graph
onto Android for what is one small request. OkHttp was already present for the
cloud speech engine. This is a deliberate deviation from the usual
"always use the official SDK" guidance.

**Consequences.** The request shape is maintained by hand: `output_config` with a
`json_schema` format for structured output, `effort: "low"` (a title and a date
need no deep reasoning), and adaptive thinking left at its default. The response
is scanned for the `text` block rather than assuming `content[0]`.

---

## 7. Speech models are fetched, not committed — superseded by 17

*Decided 2026-08-24; superseded 2026-10-01.*

The models were bundled into the APK from a gitignored `assets/vosk/` folder, filled by
`scripts/fetch-models.sh` and enforced by a `checkSpeechModels` Gradle task. That kept
~78 MB out of git history, but it left a build that could not run without a manual step
and a 90 MB APK. Both are gone: the models are no longer part of the build at all
(decision 17), and the script and the Gradle task were deleted.

---

## 8. Hand-written dependency graph, no DI framework

**Decision.** `AppContainer` constructs everything lazily.

**Why.** The graph is one screenful with no runtime substitution. An annotation
processor would cost more build time and reader effort than it saves.

**Consequences.** ViewModels are built by `viewModelFactory` initialisers that
read the container out of `CreationExtras`.

---

## 9. Record AAC, then decode to PCM for recognition

**Decision.** `AudioRecorder` writes 16 kHz mono AAC in an MP4 container;
`PcmDecoder` decodes it back to PCM to feed Vosk.

**Why.** The recording is an email attachment, so size matters — AAC is ~240 KB
per minute against ~1.9 MB for raw WAV. Vosk needs PCM. Decoding afterwards
keeps both properties.

**Rejected.**
- *Record raw PCM* — simple, but attachments get large.
- *Stream from the mic into Vosk live* — `SpeechService` opens its own
  `AudioRecord`, which would fight `MediaRecorder` for the microphone.

**Consequences.** `LinearResampler` exists because not every device's AAC encoder
honours the requested 16 kHz, and a Vosk model fed the wrong rate returns
confident nonsense rather than an error. It carries state across chunk
boundaries and is unit-tested for drift.

---

## 10. Secrets are sealed with an Android Keystore key

**Decision.** `SecretStore` encrypts the SMTP password and API keys with
AES-GCM using a key generated inside the Android Keystore.

**Why.** The key material never enters the app process, and a copied
preferences file is useless on another device.

**Consequences.** Backups deliberately exclude `secrets.preferences_pb` — a
restored copy would be undecryptable. Passwords are re-entered once after a
restore. The `<exclude>` sits inside a `datastore` `<include>`, because an
exclude outside any include is inert (and a lint error).

---

## 11. arm64 only

**Decision.** `abiFilters` is limited to `arm64-v8a`.

**Why.** `libvosk.so` is ~10 MB per ABI. Including 32-bit ARM added 9 MB for
devices that effectively do not exist at minSdk 26; x86 is emulator-only.

**Consequences.** The APK will not install on a 32-bit device or an x86 emulator.
Adding `"armeabi-v7a"` back is a one-line change, noted in `build.gradle.kts`.

---

## 12. Swipe directions

**Decision.** Swipe left completes; swipe right deletes, with an undo snackbar.

**Why.** Specified by the user.

**Consequences.** Both handlers react to the settled value and call `reset()`,
letting the data change drive the row off the list. Vetoing via
`confirmValueChange` is deprecated, and would strand a row in a dismissed state
when a completed brainwave stays visible under the "Done" filter.

---

## 13. Calendar entries are written directly, not emailed — **user choice**

*Decided 2026-10-01. Supersedes the original "send a calendar invite by email".*

**Decision.** `CalendarWriter` inserts the event into the phone's calendar through
`CalendarContract`. No `.ics` is generated and no calendar mail is sent.

**Why.** The emailed invite never produced a calendar entry. Evidence from the test
phone: both invite jobs finished `SUCCEEDED` on their first attempt, so the SMTP server
accepted them — and nothing appeared in the calendar. Whether a mail provider turns a
self-sent `METHOD:REQUEST` message into an entry is the provider's decision, and the
app has no way to observe it. Writing to the calendar directly removes the middleman.
It is also a platform API, so it keeps decision 5 intact. The user asked for exactly
this ("rather have it directly make the entry in my calendar").

**Rejected.**
- *Fix the `.ics` so Gmail accepts it* — unverifiable from the app, and a different
  provider would behave differently again.
- *Keep both* — if a mail client did add the invite there would be two entries.

**Consequences.**
- Two runtime permissions (`READ_CALENDAR`, `WRITE_CALENDAR`), requested when first
  needed. A denied permission never blocks saving a brainwave; it only means no entry.
- Timed brainwaves become 30-minute events with an alarm; day-only ones become all-day
  events (UTC midnight to UTC midnight, see CLAUDE.md).
- Editing updates the entry, completing or deleting removes it, reopening re-adds it.
  The entry's id is stored in `Brainwave.calendarEventId`.
- Room schema v2 drops `inviteSent`, `uid` and `icsSequence`. See `Migrations.kt`.
- `IcsBuilder` and the invite mail are gone.

---

## 14. The app never guesses which calendar

*Decided 2026-10-01.*

**Decision.** `resolveTarget` returns the calendar the user chose, else the phone's
*only* writable calendar, else nothing. With several and none chosen the app asks, and
writes nothing until answered.

**Why.** The first design preferred the account's primary calendar. The test phone's
primary calendar is a work calendar, alongside a shared family calendar and others. Code cannot tell a work calendar from a personal one, and
a note about the shopping silently landing in the wrong one is worse than a note not
landing at all.

**Consequences.** Choosing or changing the calendar *moves* existing entries
(`rehomeCalendarEntries`); otherwise their stored ids would make the catch-up believe
they still exist and the new calendar would never receive them. `catchUpCalendar()` runs
on every start to cover entries missing for reasons that have since gone away.

---

## 15. Every database write is targeted

*Decided 2026-10-01.*

**Decision.** The DAO exposes single-purpose updates; callers do not write back a whole
`Brainwave` they read earlier. `BrainwaveCoordinator` re-reads the row before deciding
anything.

**Why.** Observed on the test phone: every brainwave had `noteMailSent = 1` and
`inviteSent = 0` although both jobs had succeeded. The note and invite workers ran
concurrently, each read the row, set its own flag and wrote the *whole row* back, and the
slower worker erased the other's flag. The same pattern would have made the edit screen
overwrite a freshly stored `calendarEventId` with a stale null, producing a duplicate
calendar entry on the next save.

**Consequences.** More DAO methods, no lost updates. A new column means a new targeted
query, not a `copy()`.

---

## 16. Spoken numbers are normalised before any date matching

*Decided 2026-10-01.*

**Decision.** `DueDateParser` first rewrites spelled-out numbers to digits ("half drie"
→ "half 3", "vijftien oktober" → "15 oktober", "three thirty" → "3 30"), and only then
looks for dates and times. A `Mapped` text type keeps, for every character, the slice of
the original it came from, so matches are reported as original text.

**Why.** Vosk writes what it hears, and it hears words. The first version of the parser
matched digits only, so every time or date given in words was invisible to it: on the
test phone the due dates of recordings like "morgenvroeg om zeven uur" had to be set by
hand. This affects English as much as Dutch.

**Consequences.**
- `ParsedDue.matchedSpans` (original text slices) replaced a single matched string,
  because a date phrase is rarely contiguous — "vrijdag *middag* om twee uur".
- AM/PM is decided by day-part words ("'s middags", "morgenvroeg"), then by the hour as
  spoken, then by a 1–7 → afternoon heuristic. Dutch idioms ("half drie" = 2:30, "tien voor
  acht" = 7:50) are handled on the *spoken* hour.
- Dutch bare "een" is the article, so only "één" counts as the number, except directly
  before a month name. Ordinals are only rewritten next to a month ("the third of march",
  not "the second thing").
- The tests use real recognizer output (`SpokenTranscriptTest`), because typed input
  would have passed the old parser.
- Not fixable here: recognition errors in the small models ("overmorgen" → "vanmorgen",
  "renew" → "the new"). A date-vocabulary grammar for the spoken due-date answer is a
  candidate for later, not done.

---

## 17. Speech models are downloaded on first use, not bundled — to publish on F-Droid

*Decided 2026-10-01. Reverses the bundling choice of decisions 1 and 7.*

**Decision.** The APK contains no speech model. `SpeechModelManager` downloads the one
for the chosen language from `alphacephei.com` the first time it is needed, after asking
the user. `SpeechModelCatalog` pins each model's URL, exact size and SHA-256; the
download is trusted only if it matches.

**Why.** The goal is F-Droid, which builds the app from source on its own servers and
accepts prebuilt binaries only from a short list of trusted package repositories. A
model file fetched from a project website at build time is not on that list, and bundling
81 MB of weights into the source tree is not acceptable either. Apps that use Vosk on
F-Droid (Dicio, for one) download the model at runtime — the one pattern with precedent.
Both models are Apache 2.0 (per the upstream catalogue) and `libvosk.so` comes from Maven
Central, which F-Droid allows for FLOSS binaries.

**Rejected.**
- *Keep bundling* — works on a phone, but cannot go through F-Droid's source build.
- *A build flavour that bundles for family and downloads for F-Droid* — two code paths
  to test for the sake of a 40 MB convenience.
- *Trusting whatever the server sends* — a model is a large file from the internet
  that gets unpacked on the phone. The checksum is pinned in the app, not read from the
  server, so a compromised server can make a download fail but cannot make the phone
  unpack something else.

**Consequences.**
- The release APK is ~12 MB instead of ~90 MB.
- First use needs a ~40 MB download. The recording screen asks, and recording has already
  started: the thought is caught while it asks. If the download is declined, or fails, the
  recording is kept and the review screen explains — nothing is lost (constraint 4).
  A download still running when recording stops is waited for.
- `ModelInstaller` verifies the hash *before* unpacking; rejects archive entries that
  escape the target folder; caps entry count and unpacked bytes (counted as inflated, not
  from the zip headers); and installs by swap, so a failed update never destroys a
  working model. Each of those has a test, and each was confirmed to fail when the
  guard is removed.
- Folder names match the bundled build's, and a folder without a `.sha256` marker is
  accepted, so phones upgraded from a bundled build do not re-download.
- Changing a model means changing its catalogue entry; the new hash makes the old copy
  stale and it is fetched again.
