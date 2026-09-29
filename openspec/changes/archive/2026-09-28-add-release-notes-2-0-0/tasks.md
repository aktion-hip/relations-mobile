# Tasks

## 1. Version and store changelogs

- [x] 1.1 In `app/build.gradle`, set `versionCode 2` and `versionName "2.0.0"`, and enable `buildFeatures { buildConfig true }`; verify that `aapt2 dump badging` on the release APK shows `versionCode='2' versionName='2.0.0'`
- [x] 1.2 Create `fastlane/metadata/android/en-US/changelogs/2.txt` and `fastlane/metadata/android/de-DE/changelogs/2.txt` with the D5 texts (UTF-8 without BOM, LF line endings); verify that each file is at most 500 characters (363 EN, 412 DE)

## 2. In-app "What's new"

- [x] 2.1 Add `whats_new_title` ("What's new" / "Neuigkeiten") and `whats_new_text` (the D5 texts, with `\n` line breaks) to `values/strings.xml` and `values-de/strings.xml`; verify that `lintDebug` reports no missing translations for them
- [x] 2.2 Change `res/layout/pref_dialog_about.xml` to a `ScrollView` root around the existing rows, and append the heading and body `TextView`s after the search-index row (D3); verify that `assembleDebug` succeeds and the existing view ids are unchanged, so `AppInfoFragment` compiles without edits

## 3. Consistency test

- [x] 3.1 Add `app/src/test/java/org/elbe/relations/mobile/ReleaseNotesConsistencyTest.kt`. For `en-US`↔`values` and `de-DE`↔`values-de`, it reads the changelog for `BuildConfig.VERSION_CODE`, extracts `whats_new_text` from `strings.xml`, normalizes both (D1) and asserts they are equal. It also asserts the file exists, has no BOM and is at most 500 code points, naming the locale or file in each failure message. Verify with `./gradlew testDebugUnitTest`
- [x] 3.2 Verify the test fails as intended: temporarily change one word in `de-DE/changelogs/2.txt` (expect a failure naming `de-DE`), and temporarily set `versionCode 3` (expect a failure naming the missing `3.txt`); then revert both

## 4. Verification and bookkeeping

- [ ] 4.1 Manual check on a device or emulator (API 23 and API 36, English and German device language, portrait and landscape on a small screen): Settings → Application Information shows version 2.0.0, the counts, and the complete "What's new" text in the device language, and everything can be reached by scrolling
- [x] 4.2 In `openspec/changes/migrate-to-jvm21-sdk36/tasks.md`, mark task 9.1 as done and note that it is fulfilled by `add-release-notes-2-0-0`; verify with `openspec status --change migrate-to-jvm21-sdk36`
