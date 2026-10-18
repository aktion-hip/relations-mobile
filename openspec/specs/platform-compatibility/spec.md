# platform-compatibility Specification

## Purpose

Defines which Android versions RelationsMobile supports, how it behaves under the platform rules of its target API level, and the guarantee that upgrading the app keeps the user's on-device data.

## Requirements

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

### Requirement: Existing data survives the upgrade
Upgrading from version 1.0.0 to the migrated version SHALL keep the user's imported items and relations, the search index, and all stored preferences except the unsupported cloud-provider selection covered by the `cloud-sync` capability. The upgrade SHALL NOT require a fresh import from the cloud.

#### Scenario: In-place upgrade keeps items
- **WHEN** a device with version 1.0.0 and an imported database is upgraded in place to the migrated version, and the app is launched
- **THEN** the Terms, Texts and Persons tabs list the same items as before the upgrade, and the related items of an item are still shown

#### Scenario: In-place upgrade keeps the search index
- **WHEN** after the in-place upgrade the user searches for a term that returned results before the upgrade
- **THEN** the search returns the same results without the index being rebuilt

#### Scenario: In-place upgrade keeps preferences
- **WHEN** after the in-place upgrade the user opens Settings
- **THEN** the index language, the long-press setting and the Dropbox or Azure configuration show their earlier values, and the intro screen is not shown again

### Requirement: Edge-to-edge layout
With edge-to-edge display enforced at API 36, every screen SHALL keep its toolbar, tabs, lists and dialogs fully visible and usable. No interactive element or text SHALL be hidden behind the status bar, the navigation bar or a display cutout.

#### Scenario: Main screen insets
- **WHEN** the main screen is shown on an API 36 device with gesture navigation and with three-button navigation
- **THEN** the toolbar sits below the status bar, and the last list item can be scrolled fully above the navigation bar

#### Scenario: Secondary screens insets
- **WHEN** the item detail, related items, settings and intro screens are shown on an API 36 device
- **THEN** no content or control on them overlaps the system bars

### Requirement: Back navigation
Back navigation SHALL work with the system back gesture and the back button on API 36, where predictive back is enabled, as well as on older versions. Going back SHALL follow the declared parent hierarchy: item detail and related items return to the main screen.

#### Scenario: Back from item detail
- **WHEN** the user opens an item and then performs the system back gesture
- **THEN** the app returns to the main screen with the tab that was selected before

#### Scenario: Back from settings
- **WHEN** the user opens Settings and presses back or uses the toolbar's up arrow
- **THEN** the app returns to the screen from which Settings was opened

### Requirement: Explicit component export
Only the launcher activity SHALL be exported, and every activity SHALL declare its exported state explicitly.

#### Scenario: Merged manifest export flags
- **WHEN** the merged manifest is inspected
- **THEN** the main activity declares `android:exported="true"` and every other activity declares `android:exported="false"`
