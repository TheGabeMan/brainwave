# F-Droid submission

`dev.gabrie.brainwave.yml` is the build recipe for
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). It is kept here so it is versioned with
the code it builds, and it is in F-Droid's **canonical format** (the output of
`fdroid rewritemeta`) because their CI rejects anything else — which is also why it carries
no comments; they are here instead.

It passes `fdroid lint` against the official category and anti-feature lists.

## Before submitting

1. The source is public at <https://github.com/TheGabeMan/brainwave>.
2. The release commit carries the tag named in `commit:` — `v1.0.0`.
   `versionName` / `versionCode` in `app/build.gradle.kts` must match the recipe.
3. Fork <https://gitlab.com/fdroid/fdroiddata>, copy the recipe to
   `metadata/dev.gabrie.brainwave.yml`, and open a merge request.

## Why `AntiFeatures: NonFreeNet`

The optional cloud speech engine (Groq by default, or any compatible server) and the optional
Claude enrichment are non-free network services the app *can* talk to. Both are off by default
and the app works completely without them. Declaring it up front is better than a reviewer
finding it; they may well drop it.

## Things that are deliberately not here

- **Screenshots and an icon image** — `fastlane/metadata/android/en-US/images/` is empty on
  purpose. Real screenshots of the app would show someone's real notes. Add some taken on a
  device you are happy to show (`phoneScreenshots/1.png`, …).
- **Signing** — F-Droid signs with its own key. Anyone who installed a build signed with yours
  must uninstall once before updating from F-Droid.

## What F-Droid will check

- All dependencies resolve from Maven Central / Google Maven and are FLOSS. `libvosk.so` comes
  from `com.alphacephei:vosk-android` (Apache-2.0).
- No prebuilt binaries in the source tree, and no model files: speech models are downloaded by
  the app at runtime and verified against a pinned SHA-256 (see `docs/decisions.md`, #17).
- The speech models are Apache-2.0 per <https://alphacephei.com/vosk/models>.
