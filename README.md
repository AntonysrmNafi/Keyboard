# BlockVeil Keyboard (Android)

> **⚠️ Status: Under active development — not ready to install yet.**
> This project is still being built out and is going through frequent structural changes, bug fixes, and layout tuning. Expect things to be incomplete or to change without notice. Please **do not download or install this app yet**; wait for a tagged release announcement. Thanks for your patience while this comes together properly.

Fully offline Android IME (input method / keyboard app).

## Project status

BlockVeil Keyboard is a work in progress. Core typing, dictionary, and settings functionality exists and is being actively refined, but the app has not reached a stable, install-ready state:

- Layouts, spacing, and sizing across keyboard modes are still being tuned for consistency
- Some features are placeholders (e.g. the emoji picker) and not yet functional
- Breaking changes can land at any time while the architecture settles

There is no estimated completion date. A release will be announced here once the app is genuinely ready for everyday use. Building and running it from source for development/testing purposes is fine, just don't expect a polished, daily-driver experience yet.

## Privacy guarantees

- No `INTERNET` permission declared in `AndroidManifest.xml`, the app cannot make network calls at the OS level
- No keystroke logging anywhere
- `allowBackup="false"`, so Android will not back up any state
- No third-party SDKs, no ads, no analytics
- Word suggestions come only from local files bundled in `assets/` (`bn_words.txt`, `en_words.txt`)

## Modes

Tap the "Aa/অ" key to cycle: English -> Bangla phonetic -> Bangla traditional -> English.

- **English**: standard QWERTY
- **Bangla phonetic**: type English letters, they convert to Bangla automatically (e.g. `ami` -> `আমি`). Use a comma inside a word to force a hasant/conjunct (e.g. `k,k` -> `ক্ক`). This is a simplified rule set (see `PhoneticEngine.kt`), not a full replica of every complex conjunct rule.
- **Bangla traditional**: direct Bangla Unicode key layout (Jatiyo-style key positions)

## Build with Android Studio

1. Install Android Studio (developer.android.com/studio)
2. Open this folder as a project (File > Open)
3. Let Gradle sync
4. Run on a device or emulator (green Run button)
5. Enable the keyboard: Settings > System > Languages & input > On-screen keyboard > Manage keyboards > turn on "BlockVeil Keyboard"
6. Switch to it from the keyboard switch icon while typing in any text field

## Build an installable APK without Android Studio (GitHub Actions)

This repo includes `.github/workflows/build.yml`. Push this repo to GitHub, then:

1. Go to the repo's **Actions** tab
2. Run the "Build APK" workflow (or push to `main`, it runs automatically)
3. When it finishes, open the run and download the `privacy-keyboard-apk` artifact (a zip containing the `.apk`)
4. Transfer/download the APK to your phone and install it (allow "install from unknown sources" if prompted)

The generated APK is unsigned, which is fine for personal installs but not for Play Store distribution.

## Expanding the dictionary

Add more words (one per line) to `app/src/main/assets/bn_words.txt` and `app/src/main/assets/en_words.txt` to improve suggestions. No code changes needed.
