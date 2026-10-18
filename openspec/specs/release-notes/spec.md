# release-notes Specification

## Purpose

Defines how RelationsMobile tells users what changed in a release: through the store changelog for each version and language, and through a "What's new" text in the app's About dialog, with both kept consistent.

## Requirements

### Requirement: Release version
The 2.0.0 release SHALL be built with `versionCode` 2 and `versionName` "2.0.0".

#### Scenario: Version in the built APK
- **WHEN** the release APK is inspected
- **THEN** it declares `versionCode` 2 and `versionName` "2.0.0"

#### Scenario: Version in the About dialog
- **WHEN** the user opens Settings, then Application Information
- **THEN** the version shown is "2.0.0"

### Requirement: Store changelog per version and language
For each released `versionCode`, the repository SHALL contain a plain-text changelog for English (`en-US`) and German (`de-DE`) at `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`. Each changelog SHALL be at most 500 characters, which is Google Play's limit for release notes.

#### Scenario: Changelogs for 2.0.0 exist
- **WHEN** the repository is inspected for `versionCode` 2
- **THEN** both `fastlane/metadata/android/en-US/changelogs/2.txt` and `fastlane/metadata/android/de-DE/changelogs/2.txt` exist and are not empty

#### Scenario: Changelog length limit
- **WHEN** any changelog file is measured in characters
- **THEN** it has 500 characters or fewer

### Requirement: Content of the 2.0.0 release notes
The 2.0.0 release notes SHALL state, in each language, that Android 6.0 or newer is required, and that Google Drive is no longer supported. They SHALL tell former Google Drive users how to continue: upload the Relations export to Dropbox or Microsoft Azure, and select that provider in the cloud configuration.

#### Scenario: Minimum Android version mentioned
- **WHEN** a user reads the 2.0.0 release notes in English or German
- **THEN** they learn that the app requires Android 6.0 or newer

#### Scenario: Google Drive migration hint
- **WHEN** a user who synchronized with Google Drive reads the 2.0.0 release notes
- **THEN** they learn that Google Drive is no longer supported, and that they can switch to Dropbox or Microsoft Azure in the cloud configuration

### Requirement: In-app "What's new"
The About dialog (Settings → Application Information) SHALL show a "What's new" section with the release notes of the installed version, in the device language (German on German devices, English otherwise). It SHALL be shown below the version and count information, and it SHALL stay readable and scrollable when the text is longer than the space available.

#### Scenario: What's new in English
- **WHEN** a user with an English (or other non-German) device language opens Application Information
- **THEN** the dialog shows a "What's new" heading followed by the English 2.0.0 release notes

#### Scenario: What's new in German
- **WHEN** a user with a German device language opens Application Information
- **THEN** the dialog shows the German heading followed by the German 2.0.0 release notes

#### Scenario: Small screens
- **WHEN** the dialog is opened on a small screen or in landscape
- **THEN** all counts and the complete release notes can be reached by scrolling

### Requirement: Store and in-app notes are consistent
For the current `versionCode`, the in-app release notes in each language SHALL have the same text as that language's store changelog. An automated check SHALL fail the unit test run if they differ, or if the changelog for the current `versionCode` is missing.

#### Scenario: Texts drift apart
- **WHEN** a developer changes the store changelog but not the in-app text, or the other way round, and runs the unit tests
- **THEN** the consistency test fails and names the language that differs

#### Scenario: Version bumped without notes
- **WHEN** `versionCode` is raised and no changelog exists for the new code
- **THEN** the consistency test fails and names the missing file
