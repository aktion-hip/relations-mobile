# Proposal

## Why

Right now RelationsMobile can only get its data through a cloud provider (Dropbox or Microsoft Azure). Each user needs a cloud account, an access token or connection string, and has to put their personal Relations data on a third-party server. Most users sync while their computer and phone are on the same WiFi, so a direct transfer from the Relations desktop application to the phone would avoid both the setup and the privacy cost.

## What Changes

- Add a third synchronization source, **Relations desktop (WiFi)**, to the cloud configuration dialog, next to Dropbox and Microsoft Azure. It needs no token.
- When this source is selected, *Synchronize Database* starts a peer-to-peer host on the phone using **jvm-libp2p** (TCP, Noise encryption). The phone announces itself on the local WiFi through mDNS and waits for the Relations desktop application. The waiting dialog also shows the phone's address, so the user can enter it on the computer when mDNS is blocked.
- The computer connects, both sides show a 6-digit code derived from the two devices' identities, and the user confirms the code on the phone. The computer then sends the data.
- The transferred data uses the same format as the cloud providers: `relations_all.zip` for a full synchronization, or one or more `relations_delta_*.zip` files for an incremental one. The existing import pipeline (`XMLImporter`, `DBImportFull`, `DBImportIncremental`, Lucene index) is reused unchanged.
- The phone tells the computer which files it imported successfully, so the computer can drop delivered deltas. This matches the cloud providers, which delete an increment after importing it.
- The waiting and receiving phases get a progress dialog that the user can cancel. It closes on its own after a timeout if no computer connects.
- **BREAKING:** The minimum Android version rises from 6.0 (API 23) to **8.0 (API 26)**, which jvm-libp2p and Netty require on Android. Devices with Android 6.0–7.1 no longer receive updates.
- Define and document a small wire protocol (libp2p protocol ID, message framing, control messages, file transfer, confirmation code). The desktop side must implement it, and it can use jvm-libp2p on the JVM too.

Non-goals:
- The desktop (Relations RCP) side. It is a separate project; this change specifies only the protocol it must speak.
- Sending data from the phone to the computer, or phone-to-phone transfer.
- Transfers across networks (relays, NAT traversal, the internet). Both devices must be on the same local network.
- The version bump and release notes. The release that ships this feature handles those under the `release-notes` capability, and its notes must say that Android 8.0 or newer is required.

## Capabilities

### New Capabilities
- `peer-to-peer-sync`: Receiving Relations data from the desktop application over jvm-libp2p on the local network. Covers discovery (mDNS and manual address), connection confirmation, the transfer protocol, full and incremental import, the WiFi prerequisite, cancellation, timeouts, and error handling.

### Modified Capabilities
- `cloud-sync`: The *Supported cloud providers* requirement changes from "exactly two cloud providers" to two cloud providers plus the Relations desktop (WiFi) source. The *Unsupported stored provider selection* scenarios list the new set of sources the dialog shows.
- `platform-compatibility`: The *Supported Android versions* requirement raises the minimum API level from 23 (Android 6.0) to 26 (Android 8.0).

## Impact

- **Code**:
  - New `p2p/` package: the protocol, framing and stream decoder, the transport interface, and the libp2p host implementation.
  - `cloud/PeerCloudProvider.kt`: implements `InteractiveCloudProvider`.
  - `cloud/SyncRunner.kt`: waiting and confirmation states, and cancellation.
  - `cloud/CloudProviderKind.kt`: a new `P2P` kind.
  - `cloud/CloudSynchronize.kt`: provider construction and the WiFi check.
  - `preferences/CloudProviders.kt`: localized source names.
  - `util/SyncObserver.kt`, `util/ProgressDialog.kt`, `util/ConfirmPeerDialog.kt`: waiting dialog with Cancel and the phone's address, and code confirmation.
  - `res/xml/cloud_provider.xml`: the new entry, with an empty hint and therefore no token field.
  - New English and German strings, and `docs/peer-to-peer-protocol.md`.
- **Build**: `minSdk` 26 in `app/build.gradle`. Remove `play-services-nearby`, and add `io.libp2p:jvm-libp2p` together with its Maven repositories (Cloudsmith, JitPack, Consensys) and the Netty packaging excludes.
- **Manifest**: Only normal permissions: `INTERNET` (already declared), `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE`. There are no runtime permissions, and Google Play services is not needed.
- **Users**: Android 6.0–7.1 devices are dropped. The feature works on phones without Google Play services.
- **External**: The Relations desktop application must implement the documented protocol before users can use this feature.
