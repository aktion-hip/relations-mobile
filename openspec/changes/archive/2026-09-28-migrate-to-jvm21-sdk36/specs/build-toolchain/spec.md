# Spec Delta

## Purpose

Defines how RelationsMobile is built: a reproducible build from a clean checkout on JDK 21, using only secure, maintained artifact repositories.

## ADDED Requirements

### Requirement: Build on JDK 21
The project SHALL compile, run lint and run unit tests with JDK 21 as the Gradle JVM. Java sources and Kotlin sources SHALL both target JVM bytecode level 21, and the build SHALL select JDK 21 through a declared toolchain rather than relying on whichever JDK happens to be on the `PATH`.

#### Scenario: Clean build with JDK 21
- **WHEN** a developer with JDK 21 installed runs `./gradlew clean assembleDebug assembleRelease` on a fresh checkout
- **THEN** the build succeeds and produces debug and release APKs

#### Scenario: Consistent bytecode target
- **WHEN** the compiled Java and Kotlin classes of the app module are inspected
- **THEN** all of them target JVM level 21, and the build reports no JVM-target mismatch between Java and Kotlin compilation

### Requirement: Gradle wrapper is the build entry point
The project SHALL include a Gradle wrapper pinned to a Gradle version that supports the configured Android Gradle Plugin and JDK 21, and it SHALL NOT require a separately installed Gradle.

#### Scenario: Wrapper-only build
- **WHEN** the build is started only through `./gradlew` (or `gradlew.bat`) on a machine without a system Gradle installation
- **THEN** the wrapper downloads the pinned Gradle distribution and the build succeeds

### Requirement: Only secure, maintained repositories
The build SHALL resolve all plugins and dependencies over HTTPS from maintained repositories, and it SHALL NOT reference jcenter or any plain-`http` repository.

#### Scenario: No insecure or sunset repositories
- **WHEN** the Gradle build scripts and settings are inspected
- **THEN** they declare no `jcenter()` repository and no repository URL that starts with `http://`

#### Scenario: Dependency resolution from a clean cache
- **WHEN** the build runs with an empty Gradle cache
- **THEN** every declared dependency resolves and the build succeeds

### Requirement: Automated tests run on the new toolchain
The existing unit tests SHALL run and pass through `./gradlew testDebugUnitTest`, and the instrumented tests SHALL compile through `./gradlew assembleDebugAndroidTest`.

#### Scenario: Unit tests pass
- **WHEN** `./gradlew testDebugUnitTest` runs with JDK 21
- **THEN** all existing unit tests execute and pass

#### Scenario: Instrumented tests compile
- **WHEN** `./gradlew assembleDebugAndroidTest` runs with JDK 21
- **THEN** the instrumented test APK builds successfully
