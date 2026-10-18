# Spec Delta

## Purpose

Defines which cloud storage providers RelationsMobile can download its Relations data from, and how the app behaves when a stored provider choice is not supported any more.

## ADDED Requirements

### Requirement: Supported cloud providers
The app SHALL offer exactly two cloud providers for synchronizing data: Dropbox (configured with an access token) and Microsoft Azure file storage (configured with a connection string). Google Drive SHALL NOT be offered.

#### Scenario: Provider list in settings
- **WHEN** the user opens Settings and then the cloud configuration dialog
- **THEN** the dialog lists Dropbox and Microsoft Azure, and does not list Google Drive

#### Scenario: Full synchronization from Dropbox
- **WHEN** Dropbox is configured with a valid access token and the user starts a full synchronization
- **THEN** the app downloads the complete Relations data file, replaces the local items and relations, rebuilds the search index, and reports completion

#### Scenario: Incremental synchronization from Azure
- **WHEN** Microsoft Azure is configured with a valid connection string and the user starts an incremental synchronization
- **THEN** the app downloads the incremental data file, applies it to the local items and relations, updates the search index, and reports completion

#### Scenario: Synchronization does not block the UI
- **WHEN** a synchronization is running
- **THEN** a progress indicator is shown, the UI stays responsive, and rotating the device does not crash the app or start a second synchronization

#### Scenario: Synchronization failure is reported
- **WHEN** a synchronization fails while downloading, because the network is unavailable, the credentials are invalid or the remote file is missing
- **THEN** the app shows an error message, and the local items, relations and search index stay as they were before the synchronization started

### Requirement: Unsupported stored provider selection
If the stored cloud-provider selection refers to a provider that is no longer supported, such as Google Drive, the app SHALL treat the provider as not configured, SHALL NOT crash, and SHALL direct the user to choose a supported provider.

#### Scenario: Sync with a stale Google Drive selection
- **WHEN** a user who had selected Google Drive in version 1.0.0 upgrades and starts a synchronization
- **THEN** the app does not crash, shows a message that the selected provider is no longer supported, and offers to open the cloud configuration

#### Scenario: Settings with a stale Google Drive selection
- **WHEN** a user whose stored selection is Google Drive opens the cloud configuration dialog
- **THEN** the dialog shows only Dropbox and Microsoft Azure, with Dropbox preselected as the default, and saving stores the chosen supported provider

