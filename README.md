# relations-mobile
Source code for the RelationsMobile Application (Android, Kotlin)

## Building

Prerequisites:

- JDK 21. The Gradle daemon requires it (see `gradle/gradle-daemon-jvm.properties`) and Gradle finds it among the
  installed JDKs; Android Studio (Narwhal 2025.1.3 or newer) bundles one.
- Android SDK with platform `android-36` (Android 16). Point `sdk.dir` in `local.properties` (or `ANDROID_HOME`) to it.

Build the APKs and run the unit tests with the Gradle wrapper:

```
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

On Windows use `gradlew.bat`. The release build is signed if the property `Mobile_Project.signing`
(see `app/gradle.properties`) points to an existing signing script.
