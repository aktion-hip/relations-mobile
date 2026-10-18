# Design

## Context

Synchronization before this change:

- `Utils` (menu `action_synchronize`, shown in `MainActivity`, `ShowItemActivity` and `ShowRelatedActivity`) calls `CloudSynchronize.synchronize(activity, resources)`. That shows the full/incremental dialog, builds a provider from `CloudProviderKind`, and hands it to `SyncRunner.instance.start(...)`.
- `CloudProvider.synchronize(incremental, progress)` is a **blocking** call that runs on `Dispatchers.IO` inside the app-wide `SyncRunner` scope, so it outlives activities and survives rotation. Its outcome is an `AbstractCloudProvider.SyncResult`.
- `SyncRunner` publishes `SyncState` as a `StateFlow`. `SyncObserver` renders it in each activity with a `ProgressDialog`.
- Import pipeline: `XMLImporter(File)` reads the first entry of a ZIP. `DBImportFull` or `DBImportIncremental` writes Room and Lucene, and `IndexWriterFactory.setOpenMode(full)` selects rebuild or update.
- The provider dialog reads `res/xml/cloud_provider.xml`. An entry with an empty `hint` already hides the token field and enables its radio button all the time, so no dialog code is needed for a token-less source.

State of the working tree: a first implementation built on Google Nearby Connections exists, from the earlier version of this plan. Its transport-independent parts carry over: the `SyncSession`/`InteractiveCloudProvider` extension of `SyncRunner` (task 3.1), the queue-based provider state machine, the confirmation and waiting dialogs, and the strings. Its Nearby-specific parts are removed: `play-services-nearby`, `PlayServicesNearbyTransport`, `NearbyPermissions`, `NearbyPermissionLauncher`/`NearbyPermissionHost`, the Bluetooth/location/nearby-WiFi permissions, and the Google Play services check.

jvm-libp2p 1.3.7 is a Kotlin/Java libp2p implementation built on Netty. Its Android sample targets minSdk 26 and Java 11. TCP, Noise and mplex are stable in it, while yamux and mDNS discovery are marked beta. It is published to Cloudsmith (`https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/`) as `io.libp2p:jvm-libp2p:1.3.7-RELEASE`, and some transitive dependencies come from JitPack and the Consensys repository.

## Goals / Non-Goals

**Goals:**
- Plug the libp2p source into the existing `SyncRunner` → `SyncObserver` flow. The import pipeline and the Dropbox and Azure providers stay unchanged.
- Keep the protocol logic (framing, messages, validation, confirmation code) in pure Kotlin, so JVM unit tests cover it.
- Test the real libp2p host end to end on the JVM, with a jvm-libp2p dialer in the unit test playing the computer's part.

**Non-Goals:**
- Making `DBImportFull` or `DBImportIncremental` transactional. A failure *during* the import behaves as it does for cloud sync today. The spec only guarantees atomicity for the transfer phase.
- Relays, hole punching, QUIC, or the libp2p DHT. The transfer only works on the local network.
- Persisting trusted computers. The code is confirmed on every synchronization.

## Decisions

### 1. The phone listens, the computer dials
The phone starts a libp2p host that listens on its WiFi IPv4 address and registers a handler for `/relations/sync/1.0.0`. The computer dials the phone and opens the stream.
*Why:* The user starts on the phone, the phone's dialog is naturally a "waiting for computer" screen, and the manual-address fallback requires the phone to be the reachable side. Desktop firewalls often block incoming connections, and phones rarely do on a home WiFi.
*Alternative:* The phone dials a listening desktop. It was rejected because the phone would need an endpoint picker, and the desktop firewall would need an exception.

### 2. Host configuration: TCP + Noise + mplex, persisted Ed25519 identity
- **Transport:** TCP only. The phone tries to listen on port **47112** so the manual address stays the same, and falls back to a port chosen by the system if that port is taken. The dialog always shows the actual port.
- **Security:** Noise. It authenticates both peer IDs, and the confirmation code in Decision 5 is based on them.
- **Muxer:** mplex, which is stable in jvm-libp2p. yamux is still beta, and only one stream is needed.
- **Identity:** an Ed25519 key pair, generated on the first peer-to-peer synchronization and stored as `filesDir/p2p_identity.key`. The data extraction rules exclude it from backups, so a restored device gets a fresh identity.
*Alternative:* A new identity per synchronization. That is simpler, but the manual address would change every time (see the spec's *Stable identity* scenario).

### 3. Discovery: libp2p mDNS plus the address in the dialog
The phone announces itself with jvm-libp2p's `MDnsDiscovery` (service `_p2p._udp.local`) while it waits. Android filters incoming multicast unless the app holds a `WifiManager.MulticastLock`, so the transport acquires one while waiting and releases it afterwards. It needs the normal permission `CHANGE_WIFI_MULTICAST_STATE`.
The waiting dialog shows the full multiaddress (`/ip4/<wifi ip>/tcp/<port>/p2p/<peer id>`) as selectable text.
*Why:* mDNS is beta in jvm-libp2p and blocked on many guest and corporate networks. The manual address always works when the two devices can reach each other.
*WiFi address:* A pure function `LocalAddress.pick(interfaces)` returns the first site-local IPv4 address of an interface that is up and not a loopback, preferring `wlan*`. If there is none, the synchronization doesn't start (spec: *WiFi prerequisite*).

### 4. Wire protocol, framing and the stream decoder
The protocol is defined in the spec and documented for desktop implementers in `docs/peer-to-peer-protocol.md`. On the phone:
- `p2p/PeerProtocol.kt`: pure Kotlin. It encodes `request`/`result`/`error` as frames and decodes and validates `hello`, `manifest` and `error` (protocol version, file-name rules for each mode, unique names, sizes of 0 or more).
- `p2p/StreamDecoder.kt`: pure Kotlin. It is fed with byte chunks exactly as they arrive from the stream. It emits complete control frames, and once a manifest is set it writes the following bytes into one `OutputStream` per file in manifest order. It reports `FileComplete(name)`, and it raises a protocol error for oversized frames (more than 65,536 bytes) or for bytes beyond the announced total.
*Why:* Netty delivers arbitrary chunk boundaries. Keeping the reassembly in one pure class lets tests cover every split (the length prefix, a frame, or a file boundary), and it lets the provider write straight into temp files, so a large ZIP is never held in memory.
*JSON:* `org.json`, which ships with Android. It stays as `testImplementation "org.json:json"` for JVM tests.

### 5. Confirmation code from the two peer IDs
`PeerProtocol.confirmationCode(localPeerId, remotePeerId)` implements the spec formula (sorted raw peer-ID bytes → SHA-256 → first 4 bytes as an unsigned integer mod 1,000,000 → 6 digits). Noise guarantees that the remote peer ID really belongs to the other end of the connection, so a device relaying the connection shows a different code.
The spec defines the formula precisely because the desktop has to produce the same digits. The protocol doc includes a worked example (two fixed peer IDs and the resulting code) that the tests check.

### 6. Transport interface and events, bridged into the existing queue
`p2p/PeerTransport.kt` has these operations:
- `start(port)`: listen and announce. Returns the listen multiaddress, or fails.
- `stopListening()`: stop accepting connections and announcing.
- `send(connectionId, bytes)`
- `close(connectionId)`
- `stop()`

It reports these events: `Connected(connectionId, remotePeerId)`, `Data(connectionId, bytes)`, `Closed(connectionId)`, `Failed(message)`.
`Libp2pTransport` implements it for Android. It wraps a pure-JVM `Libp2pHost` class that builds the jvm-libp2p host and handles protocol negotiation, which keeps it JVM-testable, and adds the multicast lock and the WiFi address.
`PeerCloudProvider` implements `InteractiveCloudProvider` with the same design as before: transport events and the user's answers (from `SyncSession.setAnswerListener`) go into one `LinkedBlockingQueue`, and a state machine on the IO thread `poll`s with deadlines. The phases are:
1. wait for connection (120 s)
2. read `hello` (stall timeout)
3. confirm the code with the user, closing any other connection
4. send `request`
5. receive the manifest and files through `StreamDecoder` into temp files in `cacheDir` (60 s stall timeout, reset on every `Data` event)
6. import
7. send `result`/`error`, then close

Because the user's answer is an event rather than a blocking call, a disconnect is still noticed while the confirmation dialog is open.
*Alternative:* Read the libp2p stream as a blocking `InputStream`. It was rejected because cancellation and the stall timeout would need a second thread, and the queue already handles both.

### 7. User interaction through `SyncRunner` (unchanged from the first implementation)
- `SyncState` values: `WaitingForPeer` (waiting dialog with Cancel), `ConfirmPeer(endpointName, token)` (accept/reject dialog; `token` holds the 6-digit code), and `Running(current, max, cancelable)`.
- `SyncSession` offers `waitingForPeer()`, `confirmPeer(name, code)`, `receiving()`, `importing()`, `progress()`, `isCanceled`, and `setAnswerListener` for `ACCEPT`, `REJECT` and `CANCEL`. `CANCEL` is ignored while importing.
- `SyncRunner.cancel()` and `answerPeer(accept)` pass the answer to the running session.
- For the manual address, `WaitingForPeer` gets an `address: String` field, so the dialog can show it after rotation too.

### 8. Receiving and importing
Files are written straight into `File.createTempFile("relationsP2p", ".zip", cacheDir)` as the bytes arrive. Only after `StreamDecoder` has reported every manifest file complete does the provider run `XMLImporter` for each file in manifest order, using `DBImportFull` for full and `DBImportIncremental` for each delta.
- **Empty incremental manifest:** the phone sends `result []`, calls `NearbyImport.noIncremental()` (renamed to `PeerImport`), which switches the preference to full, and reports the no-incremental message.
- **Import failure:** the phone sends `result` with the files already imported, or `error` if there are none.
- **Cleanup:** temp files are deleted in a `finally` block.

### 9. Source, naming and package
- `CloudProviderKind.P2P("p2p")`. The Nearby build was never released, so no stored `"nearby"` values need migrating.
- The `cloud_provider.xml` entry uses `@string/p2p_provider_name`, which is "Relations desktop (WiFi)" or "Relations Desktop (WLAN)". `CloudProviders` resolves `@string/` names.
- The package `nearby` becomes `p2p`, and the strings `nearby_*` become `p2p_*`. `cloud_provider_unsupported` (en/de) names the new source.
- `CloudSynchronize.doSync` for `P2P` runs the WiFi check and then starts the runner. There is no permission step, so the three activities no longer need a permission host.

### 10. Build and dependencies
- `minSdk` 26 in `app/build.gradle`, as the modified `platform-compatibility` spec requires. The `NewApi` lint check stays set to error.
- Add the Cloudsmith (`io.libp2p`), JitPack (`com.github.multiformats`) and Consensys (`tech.pegasys`, for `noise-java`) repositories to `settings.gradle` (`dependencyResolutionManagement`), restricted to their groups with `content { includeGroup ... }` so they can't take over other dependencies. Add `io.libp2p:jvm-libp2p:1.3.7-RELEASE` to the catalog, and remove `play-services-nearby`.
- Exclude the desktop-only Netty native artifacts (`netty-codec-native-quic`, `netty-tcnative-boringssl-static`, `netty-transport-classes-epoll`), and add the `packaging` excludes from the jvm-libp2p Android sample (`META-INF/io.netty.versions.properties`, `META-INF/INDEX.LIST`, …).
- Manifest: remove the Nearby permissions. Add `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE`; `INTERNET` is already declared.

## Risks / Trade-offs

- [Raising minSdk to 26 drops Android 6.0–7.1 users] → This is an accepted product decision, recorded in the `platform-compatibility` delta. The release shipping this change must mention it in its notes (`release-notes` capability, outside this change).
- [mDNS discovery is beta in jvm-libp2p and blocked on some networks] → The manual address in the waiting dialog always works when the devices can reach each other. The timeout message points to it.
- [Netty on Android: large dependency, APK size, META-INF clashes, reflection] → Exclude the desktop natives and apply the sample's packaging excludes, and record the APK size change in task 1.2. `minifyEnabled` stays off, as it is today, so no R8 keep rules are needed.
- [Extra Maven repositories (Cloudsmith, JitPack, Consensys) are a supply-chain and availability risk] → Restrict each repository to its groups, pin exact versions, and commit the Gradle dependency verification metadata if the project adopts it.
- [The Android WiFi address can change or be missing (e.g. mobile data, VPN)] → Check up front (spec: *WiFi prerequisite*). A lost connection is handled like any disconnect.
- [Guest or corporate WiFi with client isolation stops the computer reaching the phone at all] → Neither mDNS nor the manual address helps there. The timeout message suggests using a home network or a cloud provider.
- [An import failure after a successful transfer can still leave the data partially updated] → Unchanged from cloud sync today and documented as a non-goal. The `result` message only lists files that imported fully.

## Migration Plan

Additive for users, apart from the minSdk raise. Existing Dropbox and Azure selections and tokens stay as they are. For rollback, remove the source: a stored `"p2p"` selection then falls under the existing *Unsupported stored provider selection* handling, so no crash is possible. Lowering minSdk again would need its own change.

## Open Questions

- Whether the desktop peer should also remember the phone's peer ID to skip mDNS next time. That is a desktop feature and doesn't affect the phone side.
