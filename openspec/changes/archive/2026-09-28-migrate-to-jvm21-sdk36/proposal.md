# Proposal

## Why

RelationsMobile no longer builds on a current toolchain. It pins Android Gradle Plugin 3.x, Gradle 4/5, Kotlin 1.3, the pre-AndroidX support library, jcenter and a plain-`http` Maven repository, and it targets API 27. None of that works with JDK 21, and Google Play no longer accepts API 27 for uploads. The Google Drive integration also depends on `play-services-drive`, an API Google has shut down. Syncing from Google Drive is therefore already broken, and the dependency blocks the upgrade.

## What Changes

- Build with **JDK 21**. Java `sourceCompatibility`/`targetCompatibility` and Kotlin `jvmTarget` move to 21, via a Gradle JVM toolchain.
- Move the build to a current toolchain: Gradle 8.x wrapper, AGP 8.x (8.9 or later, for API 36), Kotlin 2.x, and KSP in place of kapt.
- Raise `compileSdk` and `targetSdk` from 27 to **36** (Android 16).
- **BREAKING**: Raise `minSdk` from 19 to **23**. Current AndroidX releases require it, so devices on Android 4.4 through 5.1 can no longer install updates.
- Migrate from `com.android.support.*` / `android.arch.*` to **AndroidX**. This covers AppCompat, Material, RecyclerView, ViewPager, Preference, Room and the test libraries, in both Kotlin sources and XML layouts.
- Replace the removed `kotlin-android-extensions` synthetic view accessors with **ViewBinding**. Six classes use them.
- Replace `AsyncTask`, which is deprecated, with Kotlin coroutines tied to lifecycle scopes. Replace the deprecated no-argument `Handler()` and `android.preference.PreferenceManager`.
- Adapt the app to targetSdk 36 platform behavior: explicit `android:exported`, `namespace` in the Gradle build instead of the manifest `package`, edge-to-edge window insets (the opt-out is gone at API 36), predictive back, and `dataExtractionRules` alongside the legacy backup rules.
- **BREAKING**: Remove the **Google Drive** cloud provider, together with `play-services-drive`, `-identity` and `-auth`, the sign-in `onActivityResult` flow and the Google-specific strings. Dropbox and Microsoft Azure stay.
- Remove dependencies that are unused or can't be resolved: IDEScout SqlScout (and its `http://` repository), `android.arch.lifecycle:extensions`, `android.arch.paging`, and jcenter.
- Update the Dropbox SDK to a current release. The Azure storage client is kept for now; see design.md.
- Keep all existing user data (the Room database, the Lucene index and preferences) across the upgrade, with no forced re-import.

## Capabilities

### New Capabilities
- `build-toolchain`: The app builds, lints and tests from a clean checkout with JDK 21 and the Gradle wrapper, with no insecure or discontinued repositories.
- `platform-compatibility`: The supported Android versions (min 23, target/compile 36), correct behavior under API 36 rules (edge-to-edge, predictive back, exported components), and keeping existing on-device data when upgrading from 1.0.0.
- `cloud-sync`: The set of supported cloud providers (Dropbox, Microsoft Azure), and how the app handles a stored provider selection that is no longer supported (Google Drive).

### Modified Capabilities
<!-- None: there are no existing specs in openspec/specs/. -->

## Impact

- **Build files**: `build.gradle`, `app/build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/*`, and possibly a version catalog (`gradle/libs.versions.toml`). The uncommitted AGP 3.5.4 / Gradle 5.4.1 changes in the working tree are replaced by this change.
- **Kotlin sources**: all 85 `.kt` files are touched by import rewrites. The substantial changes are in `cloud/*`, `MainActivity`, `ShowItemActivity`, `ShowRelatedActivity`, `IntroActivity`, `util/Utils.kt`, `util/ItemSwipeHelper.kt`, `tabs/AllSearchFragment.kt`, `preferences/*` and `data/RelationsDataBase.kt`.
- **Resources**: layouts and `xml/preferences.xml` that use fully qualified `android.support.*` tags, `xml/cloud_provider.xml`, the Google strings in `values`/`values-de`, the backup rules, and `AndroidManifest.xml`.
- **Deleted**: `cloud/GoogleDrive.kt`, `cloud/GoogleDriveProvider.kt`, `cloud/GoogleDriveService.kt`, and `preferences/AppCompatPreferenceActivity.kt` (unused).
- **Tests**: unit and instrumented tests move to AndroidX Test.
- **Users**: devices below Android 6.0 stop receiving updates. Users who picked Google Drive have to choose another provider.
- **Tooling**: Android Studio must be recent enough for AGP 8.9+ and API 36, and JDK 21 must be installed.
