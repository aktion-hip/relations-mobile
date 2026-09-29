# Tasks

## 1. Baseline cleanup

- [x] 1.1 Create the feature branch `migrate-to-jvm21-sdk36` and discard the uncommitted AGP 3.5.4 / Gradle 5.4.1 edits in `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`; verify `git diff master -- build.gradle gradle/` is empty
- [x] 1.2 Remove the SqlScout dependency, the IDEScout `http://` repository, `android.arch.lifecycle:extensions` (implementation and kapt) and `android.arch.paging:runtime` from the build scripts; verify `grep -rn -E "sqlscout|idescout|android.arch.lifecycle|android.arch.paging" --include=*.gradle --include=*.kt .` returns nothing
- [x] 1.3 Delete `preferences/AppCompatPreferenceActivity.kt` and its `<activity>` entry in `AndroidManifest.xml`; verify `grep -rn AppCompatPreferenceActivity app/` returns nothing

## 2. Remove Google Drive (D8)

- [x] 2.1 Delete `cloud/GoogleDrive.kt`, `cloud/GoogleDriveProvider.kt` and `cloud/GoogleDriveService.kt`, and remove the `google_drive` entry from `res/xml/cloud_provider.xml`; verify `grep -rln "GoogleDrive" app/src` lists only the call sites handled in 2.2
- [x] 2.2 Remove the `GoogleDriveService` fields and parameters from `MainActivity`, `ShowItemActivity`, `ShowRelatedActivity`, `Utils.runOptions` and `CloudSynchronize.synchronize`/`doSync`; delete `CloudSynchronize.synchronizeFromGoogleDrive` and `MainActivity.onActivityResult`; verify `grep -rn -E "GoogleDrive|onActivityResult|com.google.android.gms" app/src` returns nothing
- [x] 2.3 Remove the `com.google.android.gms.version` meta-data from the manifest, the `play-services-drive/-identity/-auth` dependencies, and the Google-only strings (`preference_cloud_google_title`, `preference_google_token_lbl`, `err_msg_google_drive_*`, `key_preference_google_token`) from `values`, `values-de` and `keys.xml`; verify with grep that none are left
- [x] 2.4 Replace reflection-based provider creation with a `when (id)` factory for `dropbox` and `ms_azure` that returns `null` for unknown ids, and make `doSync` show a "provider no longer supported" dialog (new strings in EN and DE) with an action that opens `SettingsActivity`; this is verified by unit test 2.6 and by 8.4
- [x] 2.5 Make the cloud configuration dialog preselect `dropbox` when the stored provider id isn't among the configured providers; this is verified by unit test 2.6 and by 8.4
- [x] 2.6 Add unit tests for the provider factory (known ids return the right provider, `google_drive` and `""` return `null`) and for the preselection fallback; they are run in 5.5

## 3. Toolchain: Gradle, AGP, Kotlin, JDK 21, API levels (D1–D3)

- [x] 3.1 Pick the latest stable AGP 8.x (8.9 or later) and its matching Gradle, Kotlin 2.2.x and KSP versions; record them in `gradle/libs.versions.toml`; update the wrapper with `./gradlew wrapper --gradle-version <v>` (using a system Gradle only if the old wrapper can't start) and verify `gradle-wrapper.properties` points to an `https://services.gradle.org` `-bin.zip` of that version
- [x] 3.2 Move repositories into `settings.gradle` (`pluginManagement` and `dependencyResolutionManagement` with `google()` and `mavenCentral()` only), convert the root and app scripts to the `plugins {}` block with catalog aliases, and remove `buildscript {}` and the stray root `android {}` block; verify `grep -rn -E "jcenter|http://" *.gradle app/*.gradle settings.gradle` returns nothing
- [x] 3.3 In `app/build.gradle`, set `namespace "org.elbe.relations.mobile"`, `compileSdk 36`, `targetSdk 36`, `minSdk 23`, remove `multiDexEnabled`, set `compileOptions` source and target to `VERSION_21`, and set `kotlin { jvmToolchain(21) }`; remove the `package` attribute from `AndroidManifest.xml`
- [x] 3.4 Replace the `kotlin-android-extensions` and `kotlin-kapt` plugins with `com.google.devtools.ksp`; raise `org.gradle.jvmargs` in `gradle.properties` to at least `-Xmx2048m` and add `android.useAndroidX=true`; pin the daemon JVM to 21 (`gradle/gradle-daemon-jvm.properties` if the Gradle version supports it, otherwise in the README)
- [x] 3.5 Enable lint `NewApi` as an error in `android { lint { ... } }` (R3); this is verified in 5.5

## 4. AndroidX and Room 2.x (D4, D5)

- [x] 4.1 Replace all `com.android.support*` and `android.arch.persistence.room*` dependencies with their AndroidX equivalents (appcompat, material, recyclerview, viewpager, preference-ktx, cardview, constraintlayout, core-ktx, fragment-ktx, activity-ktx, lifecycle-runtime-ktx, room-runtime, room-ktx, room-compiler through `ksp`) using catalog aliases; verify `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -c com.android.support` prints 0
- [x] 4.2 Rewrite the `android.support.*` and `android.arch.*` imports in all `.kt` files under `app/src` (main, test, androidTest) using the AndroidX class-mapping; verify `grep -rn -E "import android\.(support|arch)\." app/src` returns nothing
- [x] 4.3 Rewrite the fully qualified `android.support.*` tags in layouts and `res/xml/preferences.xml`, and remove the `android.support.PARENT_ACTIVITY` meta-data from the manifest; verify `grep -rn "android.support" app/src/main/res app/src/main/AndroidManifest.xml` returns nothing
- [x] 4.4 Replace `android.preference.PreferenceManager` with `androidx.preference.PreferenceManager` in all six files; verify `grep -rn "import android.preference" app/src` returns nothing
- [x] 4.5 Replace `setTargetFragment`/`fragmentManager` in `SettingsActivity.onDisplayPreferenceDialog` with `parentFragmentManager` and the AndroidX preference dialog pattern; this is verified in 8.4 (the settings dialogs open and save)
- [x] 4.6 Set `exportSchema = true` on `RelationsDataBase`, keeping `version = 1` and `Relations.db`, and configure `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`; this is verified in 5.5 (`app/schemas/.../1.json` is generated and committed)

## 5. ViewBinding and the first green build (D6)

- [x] 5.1 Enable `buildFeatures { viewBinding true }` and convert `MainActivity`, `ShowItemActivity`, `ShowRelatedActivity` and `IntroActivity` from synthetic accessors to generated bindings
- [x] 5.2 Convert `tabs/AllSearchFragment` (binding cleared in `onDestroyView`) and `util/ItemSwipeHelper` from synthetic accessors to bindings or `findViewById`; verify `grep -rn "kotlinx.android.synthetic" app/src` returns nothing
- [x] 5.3 Replace `Handler()` with `Handler(Looper.getMainLooper())` in `ItemRelatedFragment`, `SearchUI` and `AllTermsFragment`; verify `grep -rn "Handler()" app/src/main` returns nothing
- [x] 5.4 Add `packaging { resources { merges += ["META-INF/services/**"] } }` if the Lucene jars cause duplicate-resource errors, and verify that the built APK's `META-INF/services/org.apache.lucene.codecs.Codec` lists `Lucene41` (R2)
- [x] 5.5 Checkpoint: with JDK 21, verify `./gradlew clean assembleDebug assembleRelease lintDebug` succeeds with no `NewApi` errors, and that the Room schema JSON for version 1 is generated under `app/schemas/`

## 6. Sync on coroutines and the Dropbox update (D7, D10)

- [x] 6.1 Introduce `SyncRunner` (app-scoped `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, `StateFlow<SyncState>` with `Idle`/`Running(progress)`/`Done`/`Failed`, and `start()` that does nothing while `Running`), and add unit tests for the state transitions and the single-flight guard using `kotlinx-coroutines-test`; verify with `./gradlew testDebugUnitTest`
- [x] 6.2 Change `AbstractCloudProvider` from an `AsyncTask` subclass into a `CloudProvider` interface with a blocking `synchronize(incremental, progress)` run by `SyncRunner` on `Dispatchers.IO`, free of Activity references (see design D7), and port `DropboxCloudProvider` and `MSAzureCloudProvider` to it; verify `grep -rn AsyncTask app/src` returns nothing
- [x] 6.3 Wire `CloudSynchronize.doSync` to `SyncRunner.start`, and have the three activities collect `SyncState` with `repeatOnLifecycle(STARTED)` to show and dismiss `ProgressDialog`, show errors, and navigate to `MainActivity` on `Done`; this is verified in 8.4 (sync with rotation, and the failure message)
- [x] 6.4 Upgrade `dropbox-core-sdk` to the current stable 7.x release and adapt `DbxRequestConfig` and the download calls; verify it compiles, and that a full sync with a freshly generated token imports data in 8.4 (R4)
- [x] 6.5 Check `azure-storage-android:2.0.0` for dependency conflicts with `./gradlew :app:dependencies` and resolve any duplicate classes; verify `assembleRelease` passes without duplicate-class errors (R5). If it can't be resolved, stop and raise it as a scope change

## 7. API 36 platform adaptations (D9)

- [x] 7.1 Set `android:exported="true"` on `MainActivity` and `"false"` on every other activity; verify with the merged manifest (`app/build/intermediates/merged_manifests/...`)
- [x] 7.2 Call `enableEdgeToEdge()` in all activities through a shared base class or extension, and apply system-bar and cutout insets to the toolbars and app bars (top), the RecyclerViews (bottom padding with `clipToPadding=false`), the settings root and the intro pager; this is verified in 8.3
- [x] 7.3 Add `android:enableOnBackInvokedCallback="true"` to `<application>`, and confirm no `onBackPressed` override or `KEYCODE_BACK` handling is left; verify with grep and in 8.3
- [x] 7.4 Add `res/xml/data_extraction_rules.xml`, mirroring `relations_backup_rules.xml`, and reference it through `android:dataExtractionRules` while keeping `fullBackupContent`; verify `lintDebug` reports no backup-related warnings

## 8. Tests and verification

- [x] 8.1 Move the unit and instrumented tests to JUnit 4.13.2, Mockito 5.x, `androidx.test` (runner, rules, `ext:junit`), Espresso 3.6.x and `androidx.room:room-testing`, and update `testInstrumentationRunner` to `androidx.test.runner.AndroidJUnitRunner`; verify `./gradlew testDebugUnitTest assembleDebugAndroidTest` passes on JDK 21
- [ ] 8.2 Run `./gradlew connectedDebugAndroidTest` on an API 36 emulator and an API 23 emulator; verify that all instrumented tests (DB import, entry handler) pass on both
- [ ] 8.3 Run the manual edge-to-edge and back check on API 36 with both gesture and three-button navigation: main tabs, item detail, related items, settings (including the cloud and about dialogs), the intro and the sync progress dialog; verify that no content sits under the system bars, that back and up return to the parent, and that the layout works on a sw600dp tablet emulator
- [ ] 8.4 Run the manual sync check: full sync from Dropbox, incremental sync from Azure, rotation during sync (no crash, no second run), network-off failure (error shown, data unchanged), and a stale `google_drive` selection (message shown, settings preselect Dropbox); verify each `cloud-sync` scenario passes
- [ ] 8.5 Run the in-place upgrade test (R1, R7): install the signed 1.0.0 APK, import data, select a provider and index language, then install the migrated release APK signed with the same key; verify the item counts, related items, a search query and the preferences match, and that the intro isn't shown again

## 9. Release bookkeeping

- [x] 9.1 Bump `versionCode` to 2 and `versionName` to "2.0.0", and add release notes (EN/DE) stating the Android 6.0 minimum and the Google Drive removal with the migration hint; verify they're visible in the About dialog or store listing text. *Fulfilled by change `add-release-notes-2-0-0` (fastlane changelogs and the "What's new" section in the About dialog).*
- [x] 9.2 Update `README.md` with the build prerequisites (JDK 21, the Android Studio version, `./gradlew assembleRelease`); verify the documented command succeeds from a clean clone
