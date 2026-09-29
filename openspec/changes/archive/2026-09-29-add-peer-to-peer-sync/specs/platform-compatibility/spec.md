# Spec Delta

## MODIFIED Requirements

### Requirement: Supported Android versions
The app SHALL declare a minimum API level of 26 (Android 8.0) and SHALL compile against and target API level 36 (Android 16).

#### Scenario: Declared API levels
- **WHEN** the merged manifest of a release build is inspected
- **THEN** it declares `minSdkVersion` 26 and `targetSdkVersion` 36, and the app is compiled against API 36

#### Scenario: Install on the minimum version
- **WHEN** the release APK is installed on an API 26 device or emulator
- **THEN** it installs, launches to the main screen, and shows the Terms, Texts, Persons and Search tabs

#### Scenario: Install on an older unsupported version
- **WHEN** installation is attempted on a device below API 26
- **THEN** the platform refuses the installation
