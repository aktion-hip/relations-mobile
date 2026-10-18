# Tasks

## 1. Platform, dependencies and manifest

- [x] 1.1 Raise `minSdk` to 26 in `app/build.gradle` and update any minimum-Android statement in `README.md`. Verify that the merged release manifest declares `minSdkVersion` 26 and `targetSdkVersion` 36 (`platform-compatibility` scenario *Declared API levels*).
- [x] 1.2 Remove `play-services-nearby` from `gradle/libs.versions.toml` and `app/build.gradle`. Add the Cloudsmith and JitPack repositories to `settings.gradle`, each restricted with `content { includeGroup ... }`, and add `io.libp2p:jvm-libp2p:1.3.7-RELEASE` to the catalog. Exclude the desktop Netty natives and add the packaging excludes from design Decision 10. Verify that `./gradlew assembleDebug assembleRelease` succeeds with no duplicate-class or META-INF errors, and record the release APK size before and after in this task. *Recorded: the Nearby build was 11,245,597 bytes; with jvm-libp2p it is 17,407,856 bytes (+6.2 MB, mostly Netty, Guava, BouncyCastle and protobuf). A third restricted repository (Consensys, group `tech.pegasys`) is needed for `noise-java`.*
- [x] 1.3 In `AndroidManifest.xml`, remove the Nearby permissions (`BLUETOOTH*`, location, `NEARBY_WIFI_DEVICES`, `CHANGE_WIFI_STATE`) and add `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE`. Verify that the merged manifest contains no runtime permission, no Google Play services component, and unchanged activity export flags.

## 2. Protocol layer (pure Kotlin)

- [x] 2.1 Rename the package `nearby` to `p2p`, and replace `NearbyProtocol` with `p2p/PeerProtocol.kt`. It holds the constants (protocol ID `/relations/sync/1.0.0`, version 1, port 47112, maximum frame 65,536 bytes, file-name rules), frame encoding (4-byte big-endian length + UTF-8 JSON) for `request`/`result`/`error`, and decoding and validation of `hello`/`manifest`/`error` (version, mode-specific names, unique names, size ≥ 0). Verify with `PeerProtocolTest`: valid hello and manifests, an empty incremental manifest, hello version 2, invalid JSON, an unknown `type`, a wrong file name for the mode, duplicate names, and a negative size.
- [x] 2.2 Add `PeerProtocol.confirmationCode(localPeerId, remotePeerId)` following the spec formula. Verify with tests showing the code is symmetric (both orders give the same code), always has 6 digits with leading zeros, and matches a fixed example computed independently in the test (SHA-256 via `MessageDigest`).
- [x] 2.3 Create `p2p/StreamDecoder.kt`. It turns byte chunks into control frames and, after `setManifest`, into per-file output streams with `FileComplete` events. Verify with `StreamDecoderTest`: chunks split inside the length prefix, inside a frame, and across file boundaries; one-byte chunks; a zero-size file; an oversized frame; bytes beyond the manifest total, which raise a protocol error.
- [x] 2.4 Rewrite `docs/peer-to-peer-protocol.md` for desktop implementers. It covers jvm-libp2p setup (TCP, Noise, mplex), roles, mDNS and the manual multiaddress, the protocol ID, framing, the message sequence with JSON examples, the confirmation-code formula with a worked example, file transfer, timeouts and errors. Verify that the protocol test checks every JSON example in it (valid examples accepted or encoded identically, `json invalid` examples rejected) and reproduces the worked code example.

## 3. Interactive sync session in `SyncRunner`

- [x] 3.1 Extend `SyncState` with `WaitingForPeer` and `ConfirmPeer(endpointName, token)`. Add `SyncSession` and `InteractiveCloudProvider`, and `SyncRunner.cancel()` / `answerPeer(accept)`. Plain `CloudProvider`s keep working. Verify that the existing `SyncRunnerTest` still passes, and add tests for: waiting → cancel → `Failed(canceled)`; confirm → accept or reject; a second `start` rejected while waiting or confirming; `cancel()` with no sync running is a no-op.
- [x] 3.2 Add `address: String` to `WaitingForPeer`, set through `SyncSession.waitingForPeer(address)`. Verify that `SyncRunnerTest` checks the address in the published state.

## 4. libp2p transport and provider

- [x] 4.1 Replace `NearbyTransport` with `p2p/PeerTransport.kt` (`start(port)` returning the multiaddress, `stopListening`, `send`, `close`, `stop`, and the events `Connected`/`Data`/`Closed`/`Failed`), and adapt the fake transport under `app/src/test`. Verify that it compiles and is used by the tests in 4.4.
- [x] 4.2 Implement the pure-JVM `p2p/Libp2pHost.kt`: a jvm-libp2p host with TCP, Noise and mplex; the protocol handler for `/relations/sync/1.0.0`; listening on 47112 with fallback to an ephemeral port; mDNS announcement; loading or creating the Ed25519 identity from a given file. Verify with `Libp2pHostTest`, a JVM unit test in which a second jvm-libp2p host plays the computer and dials over `127.0.0.1`. It covers: `Connected` carries the dialer's peer ID; both sides compute the same confirmation code; `Data` delivers hello, manifest and file bytes; a second dialer is reported as a separate connection; the identity file yields the same peer ID after a restart.
- [x] 4.3 Implement `p2p/Libp2pTransport.kt` (Android): it wraps `Libp2pHost` and holds a `WifiManager.MulticastLock` while listening. Add the pure `p2p/LocalAddress.kt` `pick(interfaces)` and exclude `p2p_identity.key` from backups in `data_extraction_rules.xml` and `relations_backup_rules.xml`. Verify with `LocalAddressTest` (prefers `wlan*`, skips loopback and down interfaces, returns null without a site-local IPv4) and with `./gradlew assembleDebug`.
- [x] 4.4 Rework `NearbyCloudProvider` into `cloud/PeerCloudProvider.kt`, following the phases in design Decision 6. Rename `NearbyImport` to `PeerImport`. Verify with `PeerCloudProviderTest` using the fake transport. It covers every `peer-to-peer-sync` scenario that can be tested without a device:
  - full success, three deltas in order, and an empty incremental manifest (switch set to full)
  - reject, a second connection closed, the connect timeout, and the stall timeout
  - connection lost mid-transfer, and cancel while waiting and while receiving (no import called, temp files gone)
  - malformed hello or manifest, hello version 2, an oversized frame, more data than announced, and a desktop `error`
  - an import failure reporting the files already imported, and cancel ignored while importing

## 5. Source selection and sync start

- [ ] 5.1 Replace `CloudProviderKind.NEARBY("nearby")` with `P2P("p2p")`, and change the `cloud_provider.xml` entry to `@string/p2p_provider_name` with an empty hint. Keep the `CloudProviders` `@string/` name resolution. Update `cloud_provider_unsupported` (en and de) to name "Relations desktop (WiFi)". Verify that `CloudProviderKindTest` covers `fromId("p2p")`, `fromId("nearby") == null` and `selectableId` with the new id. Then check manually that the dialog lists the three sources with no token field for the new one.
- [ ] 5.2 In `CloudSynchronize.doSync` for `P2P`: remove the Play services check and the permission request, check for a WiFi address with `LocalAddress`, and start `SyncRunner` with `PeerCloudProvider`. Remove `NearbyPermissions`, `NearbyPermissionLauncher` and `NearbyPermissionHost` from `util/` and from the three activities. Verify on an emulator (API 36, and API 26 without Google Play services) that a sync on WiFi starts without a permission prompt, and that with WiFi off it shows the "connect to WiFi" message.
- [x] 5.3 Rename the `nearby_*` strings to `p2p_*` (en and de). Remove the permission and Play services strings. Add the provider name, the "connect to WiFi" message, and the waiting text with the address. Extend the no-computer message with the manual-address hint, and add the code-confirmation message. Verify that `./gradlew lint` reports no missing translations and no unused `p2p_*` strings.

## 6. UI rendering

- [ ] 6.1 In `SyncObserver`/`ProgressDialog`, show the address from `WaitingForPeer` as selectable text below the waiting message, with Cancel. `ConfirmPeerDialog` shows the computer name and the 6-digit code with Accept/Reject. Verify manually on a device: rotate while waiting and while confirming, and check that the dialog, including the address, comes back and no second sync starts. Also check that the dialogs are not hidden by system bars at API 36 (edge-to-edge).

## 7. Integration checks

- [x] 7.1 Run `./gradlew testDebugUnitTest lint assembleRelease` and confirm that everything passes, including `ReleaseNotesConsistencyTest`.
- [ ] 7.2 End-to-end manual test with a small JVM sender that uses jvm-libp2p and implements `docs/peer-to-peer-protocol.md` (the `Libp2pHostTest` dialer as a `main()`, or the desktop peer if available), with the phone on a real WiFi. Check that:
  - it finds the phone through mDNS and also connects with the manual address
  - a full sync with a real `relations_all.zip` updates the tabs and search
  - an incremental sync with two deltas applies both
  - switching WiFi off mid-transfer leaves the data unchanged

  Record the results in the change's notes.
- [ ] 7.3 Smoke test on an API 26 emulator: the release APK installs and launches to the main screen with all four tabs. Installation is refused on an API 25 emulator (`platform-compatibility` scenarios).
- [ ] 7.4 Regression: with Dropbox and with Azure selected, run a full and an incremental synchronization and confirm that the behavior is unchanged.
