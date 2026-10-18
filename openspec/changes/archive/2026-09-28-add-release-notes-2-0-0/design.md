# Design

## Context

See proposal.md for motivation and `specs/release-notes/spec.md` for the requirements.

- The About dialog is `AppInfoFragment`, a `PreferenceDialogFragmentCompat` whose layout is `res/layout/pref_dialog_about.xml`. It's a `ConstraintLayout` with label/value rows filled in `onBindDialogView`. It does not scroll: a custom AlertDialog view is not wrapped in a scroll container automatically.
- The version shown there comes from `PackageInfo.versionName` (`AboutInfoHelper`), so bumping `versionName` updates it with no code change.
- There is no `fastlane/` directory. `.gitignore` already ignores fastlane's generated outputs (`fastlane/report.xml`, `screenshots`, and so on) but not `fastlane/metadata`.
- `BuildConfig` generation is off (AGP 8 default). The unit tests run from the `app/` module directory.

## Goals / Non-Goals

**Goals:**
- One reviewed text per language, published in two places, with a test that fails if the two copies differ.
- Keep it simple enough that the next release only needs one new `<versionCode>.txt` per language plus the matching string edit.

**Non-Goals:**
- Setting up fastlane itself (Fastfile, Appfile, Play API credentials) or automating uploads.
- Showing the notes automatically on the first launch after an update. They appear only in the About dialog.
- Keeping a history of older releases in the app. Only the installed version's notes are shown.
- Any other store listing metadata (title, descriptions, screenshots).

## Decisions

### D1: The text is duplicated, with a consistency test as the guard
The changelog files and the string resources `whats_new_text` (in `values` and `values-de`) each hold the text. A JVM unit test, `ReleaseNotesConsistencyTest`, reads `../fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` and the matching `strings.xml`. It then compares the two after normalizing: CRLF becomes LF, surrounding whitespace is trimmed, and Android string escapes (`\n`, `\'`, `\"`, `\\`) are decoded. It also checks the 500-character limit, counting Unicode code points.
- *Alternative: generate the string resource from the changelog files with a Gradle task.* That is a single source, but it adds custom build logic to a small project and hides the text from translators and Android Studio's resource editor.
- *Alternative: generate the changelogs from the resources.* The store files must exist in the repository for `fastlane supply` or a manual copy anyway, so generating them saves nothing.

### D2: The test learns the current `versionCode` from `BuildConfig`
`buildFeatures { buildConfig true }` is enabled, and the test reads `BuildConfig.VERSION_CODE`. This follows the version bump automatically, and a missing `<versionCode>.txt` makes the test fail.
- *Alternative: a `systemProperty` in `testOptions.unitTests.all`.* It works too, but it is less obvious to readers than the generated constant, and `BuildConfig` costs nothing here.

### D3: The About dialog gets a scrolling "What's new" section
The root of `pref_dialog_about.xml` becomes a `ScrollView` wrapping the existing vertical `LinearLayout`; the `ConstraintLayout` is dropped, since it only centered that `LinearLayout`. After the index-count row, the layout adds a heading `TextView` (`whats_new_title`, bold, 17sp, with top padding like the other sections) and a body `TextView` (`@string/whats_new_text`, 15sp). The body is set directly in XML, so `AppInfoFragment` needs no change, and the section is visible even before the counts finish loading.
- *Alternative: a separate "What's new" preference entry with its own dialog.* That is more discoverable, but it adds a preference, a dialog class and strings. The user chose the About dialog.

### D4: Language selection through the resource system
`values-de` holds the German text, and every other locale falls back to the English `values` text. That matches both the app's existing localization and the two store locales. Play uses `en-US` as the default for other languages.

### D5: The 2.0.0 wording
English (`en-US/changelogs/2.txt` and `values/strings.xml`):

```
Relations 2.0.0
• Requires Android 6.0 or newer.
• Google Drive is no longer supported. If you synchronized with Google Drive, upload your Relations export to Dropbox or Microsoft Azure and select that provider in Settings → Cloud Configuration.
• Updated for Android 16: edge-to-edge display and the predictive back gesture.
• More reliable data synchronization.
```

German (`de-DE/changelogs/2.txt` and `values-de/strings.xml`):

```
Relations 2.0.0
• Benötigt Android 6.0 oder neuer.
• Google Drive wird nicht mehr unterstützt. Wenn Sie mit Google Drive synchronisiert haben, laden Sie Ihren Relations-Export auf Dropbox oder Microsoft Azure hoch und wählen Sie diesen Anbieter unter Konfiguration → Cloud-Konfiguration.
• Für Android 16 aktualisiert: randlose Darstellung und vorausschauende Zurück-Geste.
• Zuverlässigere Datensynchronisation.
```

The menu paths use the app's actual labels (`action_settings` → `preference_cloud_title`). Both texts stay under 500 characters. In `strings.xml`, line breaks are written as `\n`.

## Risks / Trade-offs

- **[Risk] Changelog files are saved with CRLF on Windows**, or a BOM is added. The Play Console would show stray characters, or the test would fail for no visible reason. → The test normalizes CRLF. A BOM makes the test fail with an explicit message, which is intended.
- **[Risk] The About dialog gets long on small screens.** → Handled by the `ScrollView` (D3) and checked manually on a small emulator in portrait and landscape.
- **[Trade-off] The text is duplicated** (D1). The test makes any drift visible immediately, but whoever edits one copy still has to edit the other.
- **[Dependency]** If `migrate-to-jvm21-sdk36` is still open when this change is applied, its task 9.1 must be marked as fulfilled by this change rather than done twice.
