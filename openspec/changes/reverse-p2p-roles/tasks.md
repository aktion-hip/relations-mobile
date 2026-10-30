# Tasks

Groups 1 to 6 describe the first implementation with mDNS discovery (commit `a7a87a1`); their references to design decisions point to the design of that time. Group 7 replaces the discovery with the QR code.

## 1. Dialing libp2p host

- [x] 1.1 Spike, in `Libp2pHostTest`: start two hosts on the loopback interface. One listens and announces itself with `MDnsDiscovery(host, "_relations-sync._udp", …)`, the other doesn't listen and only discovers. Verify that the host that doesn't listen receives the listener's `PeerInfo` without throwing. If `start()` throws for the non-listening host, implement the `JmDNS` query-only fallback from design Decision 2 instead, and record the outcome in this task.
  *Outcome (2026-09-30):*
  - The phone's `MDnsDiscovery.start()` throws a `NullPointerException`, because `listenPort()` needs a listen address. So the phone uses the query-only `JmDNS` fallback.
  - The service tag must carry the domain, `_relations-sync._udp.local.`. Without it `ServiceInfoImpl` throws `StringIndexOutOfBoundsException`.
  - jvm-libp2p's own answer parser (`MDnsDiscovery.Companion.Listener`) only strips the TXT length byte when it is `.` (46, i.e. 46-character `Qm…` IDs). It would fail for our 52-character Ed25519 IDs, so `Libp2pHost` parses the PTR/SRV/TXT/A answers itself.
  - With that, discovery works on loopback and on the container's `eth0`.
- [x] 1.2 Change `PeerTransport` to the interface in design Decision 3:
  - add `startDiscovery`, `stopDiscovery`, `connect(address)` and `PeerEvent.Found(peerId, addresses)`
  - remove `start` and `stopListening`

  Update `FakePeerTransport`: record `discover`, `stopDiscovery` and `connect:<address>`, add a `connectFailures` map, and add `found(...)` to emit `Found`. Verify that the project compiles with `./gradlew compileDebugUnitTestKotlin`, apart from the call sites changed in group 3.
- [x] 1.3 Rework `Libp2pHost`:
  - no listen address
  - the initiator side of `SyncProtocol` pushes `StreamHandler`, and the responder side closes the stream
  - `connect()` dials with a 10-second timeout and returns the connection id
  - discovery uses the Relations service tag

  Adapt `Libp2pTransport` (multicast lock while searching). Rewrite `Libp2pHostTest` so that its `Computer` *listens* and answers as the responder. Verify that these tests pass:
  - the phone finds the computer through mDNS on loopback
  - it connects with the computer's address and exchanges frames both ways
  - the confirmation code matches on both sides
  - a wrong `/p2p/` peer ID makes `connect()` throw
  - an address with nothing listening throws within about 10 seconds
  - the identity survives a restart

## 2. Addresses

- [x] 2.1 Create `p2p/PeerAddress.kt` (design Decision 7). Verify with a new `PeerAddressTest`:
  - accepted: `/ip4/10.0.2.2/tcp/47112/p2p/<id>`
  - rejected: no peer ID, an undecodable peer ID, a host name, octet 300, port 70000, `ip:port`
- [x] 2.2 Extend `LocalAddress`: `Interface` carries addresses with their prefix length, and `current()` returns the address and prefix. Add `rank(addresses: List<String>, local)`, which keeps the `/ip4/…/tcp/…` addresses without loopback and link-local ones and orders them: same subnet first, then site-local, then the rest (design Decision 5). Verify with `LocalAddressTest`:
  - the existing cases
  - a Windows-like list (`172.20.0.1` Hyper-V, `192.168.1.10` in the phone's `/24`, `127.0.0.1`, `169.254.x`) ranks `192.168.1.10` first and drops loopback and link-local

## 3. Search phase in the provider and the runner

- [x] 3.1 In `SyncRunner.kt`:
  - replace `SyncState.WaitingForPeer` with `SelectComputer(computers, editable)` and add `FoundComputer`
  - turn `PeerAnswer` into a sealed class with `Connect(address, computer)`
  - add `SyncSession.selectComputer(...)` and `SyncRunner.connectTo(...)`; `Connect` is only accepted in `SelectComputer`
  - treat `SelectComputer` as "running" for the single-flight check

  Verify with `SyncRunnerTest` (the existing waiting tests adapted):
  - cancel while selecting
  - `connectTo` reaches the provider
  - `connectTo` outside `SelectComputer` is ignored
  - no second start while selecting
- [x] 3.2 In `PeerCloudProvider`:
  - add a search phase: `startDiscovery`, then merge `Found` events by peer ID and republish them ranked with `LocalAddress.rank`, until `Connect`, `Cancel` (`CANCELED`) or 2 minutes (`NO_COMPUTER`)
  - then `stopDiscovery` and the connect attempts (all ranked addresses for a list selection, exactly one for a debug string); on failure `CONNECT_FAILED`
  - then the unchanged HELLO → FILES phases
  - add `editable`

  Verify with `PeerCloudProviderTest`, the existing flows adapted to "found → connect → hello":
  - two computers found, and a duplicate `Found` merged
  - cancel while searching
  - the search timeout gives `NO_COMPUTER`
  - the first address fails and the second succeeds
  - all addresses fail with `CONNECT_FAILED`, the local data unchanged, and `stop` called
  - a debug string is dialed exactly
- [x] 3.3 Add the string `p2p_connect_failed`, reword `p2p_no_computer` for the new direction, and add `p2p_searching`, in `values` and `values-de`. Remove `p2p_waiting`, and `p2p_listen_failed` if it is no longer used. Verify that `./gradlew lintDebug` reports no missing translations or unused resources for these keys.
- [x] 3.4 Added during the emulator tests: when the desktop sends invalid data, log the phase (`HELLO`, `MANIFEST`, `FILES`), the size of the received chunk, and its first 16 bytes in hex, e.g. `Invalid data in HELLO: Frame of 1380730200 bytes exceeds 65536 bytes. (chunk of 20 bytes, first bytes: 52 4c 45 58 …)`. Verify with `PeerCloudProviderTest.testInvalidDataIsLoggedWithPhaseAndBytes`.

## 4. Selection dialog

- [x] 4.1 Create `util/SelectComputerDialog.kt` and its layout (design Decision 6):
  - a single-choice list and an empty "Searching…" state
  - in debug builds, an `EditText` prefilled from `p2pDebugConnectAddress`; selecting a computer overwrites it
  - Connect enabled on a selection, or on non-empty text in debug builds; an invalid entry sets an error and keeps the dialog open
  - Cancel calls `SyncRunner.cancel()`
  - `update(computers)` keeps the selection and the text, and both survive rotation

  Render it from `SyncObserver` for `SelectComputer`, and dismiss it for the other states. Verify that `./gradlew assembleDebug assembleRelease` succeeds.
- [x] 4.2 In `CloudSynchronize`, apply the WiFi check only when `!BuildConfig.DEBUG`, and pass `editable = BuildConfig.DEBUG` to `PeerCloudProvider.create`. Verify that `./gradlew testDebugUnitTest` passes, except for the three known time-zone tests. With `TZ=Europe/Zurich` all of them pass.

## 5. Desktop contract

- [x] 5.1 Rewrite `docs/peer-to-peer-protocol.md` for the new roles (now design Decision 8):
  - parameters: the desktop listens on 47112 or a fallback port, with the service tag `_relations-sync._udp`
  - "Offering the data": show the multiaddresses and peer ID
  - the phone dials and opens the stream; the desktop accepts one stream per synchronization
  - `hello` is sent on stream open
  - firewall and emulator (`10.0.2.2`) notes
  - the unchanged framing, sequence, code and invalid messages

  Verify that `PeerProtocolTest`'s documentation checks still pass, i.e. every JSON example and the worked code example still match.

## 6. Integration checks

- [x] 6.1 In `Libp2pHostTest`, add an end-to-end test that runs a listening desktop stand-in, the real `Libp2pHost` and `PeerCloudProvider` through discovery → connect → confirm → full transfer on loopback. Verify that the provider returns success, the received file matches, and the desktop receives the `result` frame.
- [ ] 6.2 On a debug build in the Android emulator, with a desktop that implements the new contract listening on the host, type `/ip4/10.0.2.2/tcp/<port>/p2p/<desktop ID>` and tap Connect. Verify the confirmation and the transfer, that the field is empty at the next synchronization, and that rotating with typed text keeps the text.
- [ ] 6.3 On a release build on a phone on the same WiFi as the desktop, scan the desktop's QR code. Verify that the phone connects without another tap, that the synchronization succeeds, that the camera permission was requested only on the first scan, and that after denying it pasting the copied text still works.
- [x] 6.4 Run `openspec validate reverse-p2p-roles --strict` and verify that it passes. Rerun it after group 7.

## 7. QR code instead of mDNS discovery

- [x] 7.1 Remove discovery from the transport (design Decisions 2 and 3): `startDiscovery`, `stopDiscovery` and `PeerEvent.Found` from `PeerTransport` and `FakePeerTransport`; JmDNS, `parseAnswer` and the `discoveryAddress` parameter from `Libp2pHost`; the multicast lock from `Libp2pTransport`. Remove the discovery tests from `Libp2pHostTest` (`testDiscovery`, `testDiscoveryWithoutAddress`, `testParseAnswer`), and change `testProviderEndToEnd` to connect with the desktop stand-in's address. Verify that `Libp2pHostTest` passes.
- [x] 7.2 Remove `LocalAddress.rank`, `Local.sameSubnet` and the prefix length; `current()` returns the `Inet4Address` again (design Decision 5). Verify that `LocalAddressTest` passes with the `pick` cases.
- [x] 7.3 In `SyncRunner.kt`: `SelectComputer` without a list and without `editable`, `PeerAnswer.Connect(address)`, and remove `FoundComputer`. In `PeerCloudProvider`: wait for `Connect` for at most 2 minutes (`NO_COMPUTER`), then exactly one `connect()` (`CONNECT_FAILED`); remove the `editable` and `localAddress` parameters and `SEARCH_FAILED` (design Decision 4). Verify with the adapted `SyncRunnerTest` and `PeerCloudProviderTest`:
  - connect with an address
  - cancel and the 2-minute timeout
  - connect fails, and a wrong peer ID (`connect()` throws), both `CONNECT_FAILED`
  - the unchanged HELLO-to-FILES tests
- [x] 7.4 Add `com.journeyapps:zxing-android-embedded` to `gradle/libs.versions.toml` and `app/build.gradle` (design Decision 9). In `AndroidManifest.xml`, remove `CHANGE_WIFI_MULTICAST_STATE` (and `ACCESS_WIFI_STATE` if nothing uses it anymore), and declare the camera feature with `required="false"`. Verify that the merged release manifest contains `CAMERA`, no multicast permission and no Google Play services component, and that `./gradlew assembleRelease` succeeds. Record the release APK size before and after in this task.
  *Recorded (2026-10-30, unsigned release APK):* 17,430,024 bytes with mDNS (commit `398dd0e`), 17,681,024 bytes with ZXing and without mDNS (+251,000 bytes). The merged manifest declares `CAMERA`, all camera features with `required="false"`, no `CHANGE_WIFI_MULTICAST_STATE` or `ACCESS_WIFI_STATE`, and no Google Play services component. ZXing's `CaptureActivity` loses its fixed orientation (`tools:remove`), so the scan screen follows the device; a fixed `fullSensor` would have triggered lint's `DiscouragedApi` (Android 16).
- [x] 7.5 Rebuild `SelectComputerDialog` and its layout (design Decision 6):
  - *Scan QR code*, the empty field, Cancel and Connect
  - scan via `ScanContract`: a valid result connects right away, an invalid one sets the text and the error, a canceled scan changes nothing
  - Connect checks with `PeerAddress.parse` and keeps the dialog open on an error
  - remove the list, `BuildConfig.DEBUG` and `p2pDebugConnectAddress`; adapt `SyncObserver`
  - strings in `values` and `values-de`: new `p2p_scan` and `p2p_address_hint`, `p2p_select_title` and `p2p_no_computer` reworded, `p2p_searching` and `p2p_search_failed` removed

  Verify that `./gradlew assembleDebug assembleRelease lintDebug` succeeds without missing translations or unused resources for these keys.
- [x] 7.6 In `CloudSynchronize`, apply the WiFi check in all builds, and remove the `editable` and `localAddress` arguments. Verify that `./gradlew testDebugUnitTest` passes (with `TZ=Europe/Zurich`).
- [x] 7.7 Update `docs/peer-to-peer-protocol.md` (design Decision 8): no mDNS, one address as a QR code and as text, picking the interface, the emulator. Verify that `PeerProtocolTest`'s documentation checks pass.
