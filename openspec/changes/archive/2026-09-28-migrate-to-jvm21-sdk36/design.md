# Design

## Context

See proposal.md (Why) for motivation, and the specs under `specs/` for required behavior. This section records only the facts about the current code that shape the approach.

- **Build**: there is one `:app` module with Groovy build scripts. Versions are hard-coded in `ext {}`, with no version catalog. The working tree holds an uncommitted, partial bump to AGP 3.5.4 / Gradle 5.4.1, including an `android { compileOptions }` block in the *root* `build.gradle` that has no effect there. Signing is applied from an external file named by the `Mobile_Project.signing` property.
- **Libraries that go away when AndroidX arrives**: `android.support.*` is used in about 25 distinct imports, and also as fully qualified tags in layouts and `xml/preferences.xml`. `android.arch.persistence.room` 1.1.1 runs through kapt. `android.arch.lifecycle:extensions` and `android.arch.paging` are declared but never used in code. SqlScout is declared but never referenced.
- **Removed Kotlin plugin**: `kotlin-android-extensions` synthetic accessors are used in `MainActivity`, `ShowItemActivity`, `ShowRelatedActivity`, `IntroActivity`, `util/ItemSwipeHelper` and `tabs/AllSearchFragment`.
- **Concurrency**: `cloud/AbstractCloudProvider` extends `AsyncTask` and holds a strong reference to the `AppCompatActivity`. `onPreExecute`, `onProgressUpdate` and `onPostExecute` drive `util/ProgressDialog`, a `DialogFragment`. When the sync finishes, the app restarts `MainActivity`. `GoogleDriveProvider` and `CloudSynchronize` also use `AsyncTask`. Three UI classes create `Handler()` without a Looper.
- **Provider wiring**: `xml/cloud_provider.xml` lists the providers by fully qualified class name. `CloudSynchronize` instantiates them by reflection through a fixed four-argument constructor. An unknown provider id makes the class name `""`, so `Class.forName("")` throws. This is exactly what a stored `google_drive` selection will hit once the class is removed.
- **Google Drive coupling**: `GoogleDriveService` is created in `MainActivity`, `ShowItemActivity` and `ShowRelatedActivity` and passed through `Utils.runOptions(...)` and `CloudSynchronize.synchronize(...)`. `MainActivity.onActivityResult` exists only for the Google sign-in.
- **Data**: the Room database is `Relations.db`, schema `version = 1`, `exportSchema = false`, with no migrations. The Lucene 4.1 index lives in `getExternalFilesDir(null)/<LUCENE_PATH>`. Preferences use the default `SharedPreferences` file through the deprecated `android.preference.PreferenceManager`.
- **Settings**: `SettingsActivity` already uses `AppCompatActivity` with `PreferenceFragmentCompat`. `AppCompatPreferenceActivity` is registered in the manifest but not used. Dialogs are shown with `setTargetFragment`, which is deprecated.
- **Manifest**: it uses the `package` attribute and no `android:exported`. It uses `fullBackupContent` only. No activity overrides `onBackPressed` or sets `screenOrientation`.

## Goals / Non-Goals

**Goals:**
- Reach a buildable, testable, current toolchain in a sequence of steps where each one still builds, so that a failure can be pinned to a single step.
- Keep the on-device formats unchanged, which makes the upgrade data-safe without any migration code.
- Keep behavior the same apart from what the specs require, such as the Google Drive removal and the API 36 platform adaptations.

**Non-Goals:**
- Migrating to AGP 9, the Kotlin DSL (`build.gradle.kts`), Jetpack Compose, Material 3 theming, Navigation or Paging 3.
- Replacing Lucene 4.1, for example with Room FTS or a newer Lucene. Newer Lucene versions need Java APIs that Android lacks.
- Replacing the legacy Azure storage SDK or reworking how Dropbox authenticates (OAuth/PKCE instead of pasted tokens).
- Making the full import transactional or atomic. The current clear-then-insert behavior stays.
- Enabling R8 minification. `minifyEnabled` stays `false`; see the risk about reflection.
- Supporting Google Drive through the REST API. That would be a separate change.

## Decisions

### D1: Toolchain versions: AGP 8.x, pinned at the latest stable release at implementation time
The build uses the newest stable **AGP 8.x** release. It must be 8.9 or later, the first line that supports compileSdk 36; at the time of writing that is the 8.13 line. **Gradle** is the version that AGP release requires, **Kotlin** is 2.2.x with the matching **KSP** (*as built:* AGP 8.13.2, Gradle 8.14.5, Kotlin 2.2.21, KSP 2.2.21-2.0.5; `androidx.core` is pinned to 1.18.0 because 1.19+ requires compileSdk 37 and AGP 9.1), and the build uses the **Groovy DSL** it has today. Versions move into `gradle/libs.versions.toml`, and plugins are applied through the `plugins {}` block, with repositories declared in `settings.gradle` (`pluginManagement` / `dependencyResolutionManagement`).
- *Alternative: AGP 9.x.* It changes more defaults, such as built-in Kotlin and stricter DSL removals, and would pile a second migration onto this one. It's deferred.
- *Alternative: keep `ext {}` for versions.* It works, but the catalog gives Android Studio upgrade hints and puts every version in one place. The extra cost is small because every dependency line changes anyway.

### D2: JDK 21 through toolchains, not only `compileOptions`
The build sets `kotlin { jvmToolchain(21) }`, which also sets the Java toolchain, and explicitly sets `compileOptions { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_21 }`. The Gradle daemon JVM is pinned to 21 through `gradle/gradle-daemon-jvm.properties`, falling back to documentation in the README if the chosen Gradle version doesn't support it. The stray `compileOptions` block in the root `build.gradle` is removed.
- *Why toolchains:* a Java/Kotlin JVM-target mismatch is an error in Kotlin 2.x, and a toolchain keeps both targets in step no matter which JDK Android Studio has bundled.
- *Note:* D8 desugars Java 21 bytecode for the device. Java 21 **library** APIs, such as `SequencedCollection.getFirst()` or `List.removeFirst()`, are only present on API 35 and later. The build relies on lint `NewApi` as an error to catch them; see R3.

### D3: API levels: compile/target 36, min 23
These come directly from the proposal. minSdk 23 is the floor of current AndroidX releases, and it also makes `multiDexEnabled` unnecessary, because native multidex is available from API 21. The flag is removed.

### D4: AndroidX in a single step, as a mapping-driven rewrite plus manual cleanup
The support-to-AndroidX switch happens as one step. Imports and fully qualified names in `.kt` and `.xml` files are rewritten from the official AndroidX class-mapping CSV, or with Android Studio's "Migrate to AndroidX" action if a JDK the old AGP accepts is available. After the automated pass, the remaining spots are fixed by hand: fully qualified XML tags (`android.support.v7.preference.*`, `android.support.design.widget.*`), the manifest `android.support.PARENT_ACTIVITY` metadata (removed, because `parentActivityName` is enough on API 16 and later), and test runner imports. Jetifier is not enabled, because no remaining third-party dependency needs it once Drive and SqlScout are gone. `./gradlew checkJetifier`, or a dependency-tree search for `com.android.support`, confirms this.
- *Alternative: keep `android.enableJetifier=true`.* It isn't needed, and it slows the build.

### D5: Room 1.1.1 to Room 2.x with KSP, and the schema left as it is

*Verified during implementation:* Room 1.1.1, run on the 1.0.0 entity definitions, and Room 2.8.5, run on the migrated code, compute the same identity hash (`a633dd3b2b99031e0b139b57cc2e31a7`). An existing database therefore opens without a migration.
The app moves to the current stable `androidx.room` release with `room-ktx`, and switches kapt to KSP. `@Database(version = 1)` and the database name `Relations.db` stay unchanged, so Room opens the existing file without a migration: the identity hash is computed from the schema, and the schema doesn't change. `exportSchema = true` is enabled with `room.schemaLocation` set to `app/schemas/` and committed, so any later schema change is visible in review and can be tested against a real migration.
- *Alternative: bump the version with an empty migration.* That adds risk for no benefit.
- *Verification:* the upgrade scenario in `platform-compatibility` is tested with a real 1.0.0 install; see the migration plan.

### D6: ViewBinding replaces the synthetic accessors
`buildFeatures { viewBinding = true }` is enabled. Each of the six classes inflates its binding: `Activity.setContentView(binding.root)`, and in fragments a binding released in `onDestroyView`. Views reached through `include` tags are accessed through the nested binding. The `kotlin-android-extensions` plugin is removed, and `kotlin-parcelize` isn't needed because nothing uses `@Parcelize`.
- *Alternative: `findViewById`.* It's more verbose, isn't null-safe, and offers no benefit in a codebase this size.

### D7: Sync runs on coroutines in an application-scoped runner, with the UI observing its state
`AsyncTask` is replaced by an app-scoped `SyncRunner` (singleton) that owns a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and exposes `StateFlow<SyncState>`, with the states `Idle`, `Running(current, max)`, `Done(message)` and `Failed(message)`. Any exception thrown by a provider becomes `Failed`. Providers become plain classes that implement `CloudProvider.synchronize(incremental, progress): SyncResult`, a blocking call that the runner makes on `Dispatchers.IO`. *As built:* the providers still download and import in one call. An incremental sync downloads, imports and deletes each delta file in turn, so a `download(): File` interface wouldn't fit, and the import code stays unchanged in `dbimport`. Activities collect the state with `repeatOnLifecycle(STARTED)` (`util/SyncObserver`), and show or dismiss the existing `ProgressDialog` fragment with `showNow`, so that rapid progress updates don't create duplicate dialogs. After displaying `Done` or `Failed`, the activity calls `acknowledge()`. `start()` does nothing while a sync is already `Running`.
- *Why app scope and not `lifecycleScope` or a ViewModel:* sync can start from three activities, and on success it navigates back to `MainActivity`. Cancelling mid-import on rotation or navigation would leave the tables partly cleared. An app-scoped job survives configuration changes and screen switches, which the rotation scenario in `cloud-sync` requires, and it never holds a reference to an Activity.
- *Alternative: WorkManager.* It survives process death, but it's heavyweight for an action the user starts and watches in the foreground, and it would need a foreground-service type at API 34 and later if it ran long. Deferred.
- The `Handler()` calls become `Handler(Looper.getMainLooper())`, a mechanical change with the same behavior. *As built:* there are six sites (`ItemRelatedFragment`, `SearchUI`, `AppInfoFragment` and the three `All*Fragment` tabs).

### D8: Google Drive removal and the stale selection
The three `GoogleDrive*` classes, the `google_drive` entry in `cloud_provider.xml`, the `GoogleDriveService` parameters on `Utils.runOptions` and `CloudSynchronize.synchronize`, `MainActivity.onActivityResult`, the Google strings in `values` and `values-de`, the `com.google.android.gms.version` meta-data, and the three `play-services-*` dependencies are all deleted.

Provider lookup returns `null` for an unknown id instead of `""`. `doSync` then shows the "provider no longer supported" dialog with an action that opens `SettingsActivity`, which covers the stale-selection scenarios in `cloud-sync`. Reflection-based instantiation is replaced by a `when (id)` factory over the two remaining providers, which also removes the R8 fragility. The cloud config dialog preselects `dropbox` whenever the stored id isn't in the list. The stored token under the `google_drive` key is left in place, since it's harmless, and nothing writes to it any more.
- *Alternative: rewrite a stale `google_drive` value to `dropbox` silently at startup.* It's simpler, but the user wouldn't learn why their sync source changed. The explicit message is clearer.

### D9: Adapting to API 36 platform behavior
- **Edge-to-edge**: `enableEdgeToEdge()` is called in every activity through a small base class or extension. `ViewCompat.setOnApplyWindowInsetsListener` applies system-bar and cutout insets as padding: top on the `AppBarLayout`/`Toolbar` and the settings root, and bottom on `RecyclerView`s with `clipToPadding=false`. The dark or light status-bar icons follow the theme.
- **Predictive back**: nothing overrides `onBackPressed`, and up navigation uses `parentActivityName`. The app sets `android:enableOnBackInvokedCallback="true"` and relies on the default behavior. Any back interception that turns up later uses `OnBackPressedDispatcher`.
- **Exported**: `MainActivity` gets `exported="true"`, and all other activities get `"false"`.
- **Manifest `package`**: it moves to `android { namespace "org.elbe.relations.mobile" }`, and `applicationId` stays the same.
- **Backup**: `android:dataExtractionRules` (API 31 and later) is added, mirroring the current `fullBackupContent` includes. The legacy attribute stays for API 23–30.
- **Large screens**: API 36 ignores orientation and resizability restrictions on sw600dp and wider. The app sets none, so all that's needed is a manual check in the tasks.
- **`BuildConfig`**: it isn't used, so `buildFeatures.buildConfig` stays at its default of off.

### D10: Updating the remaining third-party libraries
- **Dropbox**: moves to the current stable `dropbox-core-sdk` (*as built:* 8.0.2, the newest release at implementation time). `DbxRequestConfig(clientId)` becomes `DbxRequestConfig.newBuilder(clientId).build()`. Download calls are adjusted to the new API. The pasted-access-token model stays.
- **Azure**: `azure-storage-android:2.0.0` stays. It's legacy but a plain Java library and not affected by AndroidX. Its transitive dependencies are checked for conflicts.
- **Lucene 4.1.0**: stays, with its existing `//noinspection GradleDependency`. Any `META-INF/services` duplicates are resolved through `packaging { resources { merges += ... } }`.
- **Tests**: JUnit 4.13.2, Mockito 5.x (`mockito-core`; Mockito 1.10 doesn't run on JDK 17 or later), `androidx.test:runner`/`rules`/`ext:junit`, and Espresso 3.6.x. `room-testing` moves to AndroidX.

## Risks / Trade-offs

- **[R1] The Room 2.x identity check rejects the 1.x database**, which would crash on first open or, worse, lead someone to add a destructive fallback. → No `fallbackToDestructiveMigration`. An instrumented upgrade test covers it: install the 1.0.0 APK, import sample data, then install the migrated APK over it with the same signing key and check the counts. If the identity hash differs, add an explicit `Migration(1, 2)` that doesn't change any data.
- **[R2] The Lucene 4.1 index or codec SPI breaks under AGP 8 packaging.** Missing `META-INF/services` entries make codec lookup fail at runtime ("A SPI class of type Codec with name 'Lucene41' does not exist"). → After the build step, inspect the APK's `META-INF/services`. Run the search smoke test on an upgraded install, not only a fresh one.
- **[R3] A Java 21 library API is called on API 23–34 devices** (`NoSuchMethodError`). → Set lint `NewApi` as an error, and run the smoke test on an API 23 emulator.
- **[R4] Dropbox SDK 7 behaves differently, or Dropbox has retired the long-lived token the user pasted.** Sync fails either way, but the second case isn't something the migration causes. → Test with a freshly generated token. Record OAuth/PKCE as follow-up work.
- **[R5] The legacy Azure SDK conflicts with the new dependency set**, for example duplicate classes through Jackson or Guava. → Check with `./gradlew :app:dependencies`. If it can't be solved, fall back to a thin REST download through `HttpURLConnection` with a SAS or connection-string signature, and mark that as a scope change.
- **[R6] Edge-to-edge regressions on screens that aren't checked by hand**, such as dialogs and the intro pager. → The tasks include a visual checklist per screen, for gesture and three-button navigation, on API 36 and API 23.
- **[R7] The upgrade test is blocked by the signing key**: an in-place upgrade needs the 1.0.0 release key. → Use the existing external signing config. If it isn't available in the sandbox, run the upgrade test on the developer's machine.
- **[Trade-off] minSdk 23 drops Android 4.4 to 5.1.** Current AndroidX requires it, and it's accepted in the proposal.
- **[Trade-off] Reflection-based provider loading is replaced by a factory.** The providers are now fixed in code, but that also removes the R8 keep-rule burden (D8).

## Migration Plan

The work happens on a feature branch. Steps 1–2 are source edits. Steps 3–5 depend on each other: Kotlin 2.x no longer has the synthetic accessors, and AGP 8 on JDK 21 can't build the support-library code as it stands. They therefore land together, and their first green build (`assembleDebug` on JDK 21) is the first checkpoint. Every step after that leaves the build green. The tasks follow this order:

1. **Clean up the baseline**: discard the uncommitted AGP 3.5.4 / Gradle 5.4.1 edits; remove SqlScout and its repository, jcenter, lifecycle-extensions and paging; delete `AppCompatPreferenceActivity`.
2. **Remove Google Drive** (D8). This takes the biggest blocker out of the dependency graph before the toolchain moves.
3. **Toolchain**: Gradle wrapper, AGP 8.x, Kotlin 2.x, KSP, JDK 21 toolchain, version catalog, `namespace` (D1, D2), compile/target 36 and min 23 (D3).
4. **AndroidX** (D4) and **Room 2.x on KSP** (D5). With the old AGP not runnable on JDK 21, this is a scripted rewrite of package names using the official AndroidX class-mapping CSV, rather than the IDE's "Migrate to AndroidX" action.
5. **ViewBinding** (D6). This is the first checkpoint.
6. **Coroutines sync runner** (D7) and the Dropbox SDK update (D10).
7. **Platform adaptations** (D9).
8. **Tests and verification**: move tests to AndroidX Test, run the upgrade test (R1), the smoke tests on API 23 and API 36, and the edge-to-edge checklist.
9. **Release bookkeeping**: bump `versionCode` and `versionName` (for example 2 and "2.0.0", since minSdk and a feature are removed), and add release notes that mention Google Drive and the minimum Android version.

**Rollback**: nothing on the device changes format, so a user who reinstalls 1.0.0 over the new version keeps a working database, provided the Room schema stayed at version 1 (D5). For the code, revert the branch.

## Open Questions

- The exact version numbers for AGP 8.x, Gradle, Kotlin, KSP, Room and Dropbox are picked from the latest stable releases when step 3 starts. The constraints are in D1 and D10.
- Whether to use `gradle-daemon-jvm.properties` or only document JDK 21 depends on the Gradle version picked (D2).
