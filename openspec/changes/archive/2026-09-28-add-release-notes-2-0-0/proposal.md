# Proposal

## Why

Version 2.0.0, the `migrate-to-jvm21-sdk36` change, removes Google Drive and raises the minimum Android version to 6.0. Users need to learn both before and after they update: in the store listing when the update is offered, and in the app itself, where a user who relied on Google Drive will look once their sync stops working. The project has no release-notes channel yet.

## What Changes

- Bump the app to `versionCode` 2 and `versionName` "2.0.0". This takes over the version bump from task 9.1 of `migrate-to-jvm21-sdk36`.
- Add Play Store changelogs in fastlane's metadata layout: `fastlane/metadata/android/en-US/changelogs/2.txt` and `fastlane/metadata/android/de-DE/changelogs/2.txt`. `fastlane supply`, or a manual copy into the Play Console, picks them up. No Fastfile or fastlane tooling is added.
- Add a "What's new" section to the About dialog (Settings → About Relations → Application Information), in English and German, with the same content as the store changelog.
- Guard against the two copies (store changelog and in-app text) drifting apart, and against the store changelog going over Google Play's 500-character limit.

## Capabilities

### New Capabilities
- `release-notes`: What users are told about a release, and where: the store changelog per version and language, the in-app "What's new" text in the About dialog, and keeping the two consistent.

### Modified Capabilities
<!-- None: no existing specs in openspec/specs/ (the migrate-to-jvm21-sdk36 capabilities are not archived yet). -->

## Impact

- **New files**: `fastlane/metadata/android/{en-US,de-DE}/changelogs/2.txt`.
- **Changed**: `app/build.gradle` (version), `res/layout/pref_dialog_about.xml`, `res/values/strings.xml`, `res/values-de/strings.xml`, and possibly `preferences/AppInfoFragment.kt`.
- **Tests**: a new JVM unit test that compares the changelog files with the string resources.
- **Other change**: when this change is applied, task 9.1 of `migrate-to-jvm21-sdk36` is fulfilled by it. The migration change's archive then no longer waits on a separate release-notes decision.
