# Design

## Context

State of the working tree: commit `a7a87a1`. The phone dials, and finds the desktop through mDNS discovery (groups 1 to 6 of the tasks). That discovery is now replaced by a QR code (group 7). The dropped `add-debug-p2p-listen-address` change was removed together with its code before this change.

Flow before this change, from the archived peer-to-peer design:
- `PeerCloudProvider.synchronize()` calls `transport.start(port)` and publishes `SyncState.WaitingForPeer(address)`, which `SyncObserver` renders in the `ProgressDialog`. It then waits for `PeerEvent.Connected` and runs HELLO → CONFIRM → MANIFEST → FILES on an event queue.
- `Libp2pHost` accepts streams as the responder only.
- `PeerAnswer` is an enum (`ACCEPT`, `REJECT`, `CANCEL`). `ConfirmPeerDialog` is a `DialogFragment` that `SyncObserver` shows by tag, so it survives rotation.

Background on the removed discovery (jvm-libp2p 1.3.7 `MDnsDiscovery`):
- `start()` registers a service built from `host.listenAddresses()` *and* adds an answer listener for queries, so it fails on a host that doesn't listen.
- Its answer parser only handles 46-character `Qm…` peer IDs, not our 52-character Ed25519 IDs.

## Goals / Non-Goals

**Goals:**
- Keep the whole interactive phase (entering the address, confirming) inside the `SyncRunner` session, so that it survives rotation like the rest.
- Reuse the protocol layer unchanged: `PeerProtocol`, `StreamDecoder`, the confirmation code, and the provider's HELLO → FILES phases.
- Keep the transport testable with `FakePeerTransport`, and keep an end-to-end JVM test in which the test plays a *listening* desktop.

**Non-Goals:**
- Implementing the desktop side. It is only documented, in `docs/peer-to-peer-protocol.md`.
- Discovery on the local network, remembering addresses or trusted computers, several addresses per computer, relays, or IPv6.
- Keeping the phone-listens mode as an alternative.

## Decisions

### 1. The desktop listens, the phone dials. This reverses the archived Decision 1
The phone runs a jvm-libp2p host with no listen address (TCP, Noise, mplex, the same persisted Ed25519 identity). It dials `/ip4/<a>/tcp/<p>/p2p/<id>` with `StrictProtocolBinding.dial(host, multiaddr)`, and becomes the *initiator* of `/relations/sync/1.0.0`. The protocol handler's roles swap: `onStartInitiator` pushes the `StreamHandler`, and `onStartResponder` closes the stream ("This host does not listen").
*Why this is acceptable now:* the archived decision chose "the phone listens" because desktop firewalls block incoming connections. The desktop now has to ask for a firewall exception once (see the protocol document). In return the user flow starts on the desktop, and emulators can reach the host at `10.0.2.2`.
*Alternative:* support both directions. It was rejected because it doubles the UI and the tests for a feature that isn't released yet.

### 2. The desktop's address via QR code or text, no discovery
The desktop shows exactly one multiaddress `/ip4/<a>/tcp/<p>/p2p/<id>` as a QR code and as copyable text. The phone dials it unchanged. The desktop picks the interface itself; a desktop listening on `0.0.0.0` has to put a concrete address into the QR code (see Decision 8).
*Why:* scanning works on networks that block multicast, needs no discovery code, and the scanned peer ID tells the phone which computer it must reach.
*Removed:* the mDNS discovery implemented first (task 1.1 and group 7): JmDNS and the answer parser in `Libp2pHost`, the multicast lock, `PeerEvent.Found`, the computer list, `LocalAddress.rank`. The task 1.1 spike stays documented as history; its findings (no `MDnsDiscovery` without a listen address, the TXT bug with Ed25519 IDs) were part of the reason to drop discovery.
*Alternatives:* several addresses per QR code, ranked by the phone (rejected: the user chose one address), or one QR code per interface (rejected: the user would have to understand network interfaces).

### 3. Transport interface
```kotlin
interface PeerTransport {
    val localPeerId: ByteArray
    fun setListener(listener: ((PeerEvent) -> Unit)?)
    fun connect(address: String): String // blocking, <= 10 s; returns the connection id, Connected is emitted as before
    fun send(connectionId: String, bytes: ByteArray)
    fun close(connectionId: String)
    fun stop()
}
```
- `start(port, strictPort)` and `stopListening()` are removed, and so are `startDiscovery()`, `stopDiscovery()` and `PeerEvent.Found`.
- `connect()` throws if dialing, Noise, or protocol negotiation fails, and when the remote peer ID doesn't match. libp2p checks the peer ID during the Noise handshake against the `/p2p/` part.
- `PeerEvent.Connected` keeps carrying the remote peer ID for the confirmation code.

### 4. Waiting for the address in the provider, the address as the user's answer
- `SyncState.WaitingForPeer(address)` becomes `SyncState.SelectComputer`, an object without a list or `editable` flag.
- `PeerAnswer` becomes a sealed class: `Accept`, `Reject`, `Cancel`, and `Connect(address: String)`. `SyncRunner.answerPeer(Boolean)` stays, and `SyncRunner.connectTo(address)` is added. `Connect` is only accepted while the state is `SelectComputer`.
- In `PeerCloudProvider.synchronize()`:
  1. publish `SelectComputer`
  2. wait for `Connect` or `Cancel`, at most 2 minutes (`NO_COMPUTER`)
  3. exactly one `connect(address)`; on failure `PeerMessage.CONNECT_FAILED` with the string `p2p_connect_failed`
  4. the existing `awaitHello` and the rest

  `connect()` blocks the provider thread for at most 10 seconds. A Cancel that arrives meanwhile is handled right after the attempt returns.
- `FoundComputer`, `SEARCH_FAILED` and the provider's `editable` and `localAddress` parameters are removed. The provider still validates nothing itself: the dialog only sends addresses that `PeerAddress.parse` accepted.

### 5. `LocalAddress` for the WiFi check only
`LocalAddress.pick` and `current()` stay for the WiFi prerequisite, which now applies to all builds. `rank`, `Local.sameSubnet` and the prefix length are removed again; `current()` returns the `Inet4Address`. The emulator passes the check, because it has the site-local address `10.0.2.16`.

### 6. `SelectComputerDialog` (DialogFragment)
It is rendered by `SyncObserver` for `SelectComputer`, like `ConfirmPeerDialog`, and it is the same in every build (no `BuildConfig.DEBUG`).
- **Layout:** a *Scan QR code* button, an `EditText` for the address (empty, hint `/ip4/…/tcp/…/p2p/…`), and Cancel and Connect buttons.
- **Scan:** ZXing's `ScanContract`, registered in the fragment with `registerForActivityResult`, started with `ScanOptions` (QR codes only, `setOrientationLocked(false)`, `setBeepEnabled(false)`, a prompt text). The result arrives in the recreated fragment after a rotation. A result that `PeerAddress.parse` accepts calls `SyncRunner.connectTo(address)` right away. Any other result is put into the field with the error. A canceled scan changes nothing.
- **Connect:** enabled when the field isn't empty. The positive button is overridden in `setOnShowListener`, so that an invalid entry keeps the dialog open with the error.
- **Nothing is remembered:** the field starts empty, and `p2pDebugConnectAddress` is removed. The entered text survives rotation through the view state.
- Cancel calls `SyncRunner.cancel()`.

### 7. Connection-string parser
Pure-Kotlin `p2p/PeerAddress.kt`: `data class PeerAddress(address: Inet4Address, port: Int, peerId: String)` with `parse(text): PeerAddress?`.
- It matches `^/ip4/(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/tcp/(\d{1,5})/p2p/([1-9A-HJ-NP-Za-km-z]+)/?$` after `trim()`, and checks that each octet is 0–255 and the port is 1–65535.
- The peer ID must decode with `PeerId.fromBase58`.
- The address is built with `InetAddress.getByAddress`, so the main thread does no DNS lookup.
- `toString()` gives the normalized multiaddress.

*Alternative:* `Multiaddr(text)`. It accepts many more protocols, which would then have to be rejected one by one.

### 8. Protocol document for the desktop
`docs/peer-to-peer-protocol.md` is the desktop contract. It covers:
- the parameters table: the desktop listens on 47112, or on a port the system chooses if 47112 is taken; no mDNS
- "Offering the data": the desktop shows one multiaddress as a QR code (the multiaddress text unchanged, UTF-8, without a prefix) and as copyable text
- picking the interface: a desktop listening on `0.0.0.0` puts the address of the interface with the default route into the QR code (open a UDP socket, `connect` towards an external address, read `getLocalAddress()`; no packet is sent), and lets the user switch the interface if that is wrong
- the desktop accepts one stream and rejects other connections while a synchronization runs
- the desktop sends `hello` as soon as the stream opens, and shows the code
- the firewall: a Windows Defender prompt for incoming TCP, and what the desktop should tell the user
- the emulator: type `/ip4/10.0.2.2/tcp/<port>/p2p/<id>` into the phone's field
- the unchanged framing, sequence, confirmation code and invalid messages

### 9. ZXing as the scanner
`com.journeyapps:zxing-android-embedded` provides a ready-made scan screen through the Activity Result API (`ScanContract`).
- **Why:** it needs no Google Play services, and it adds about 0.5 MB.
- **Rejected alternatives:**
  - Google Code Scanner (`play-services-code-scanner`): needs no camera permission, but needs Google Play services, against the *WiFi prerequisite* requirement.
  - ML Kit barcode scanning with the bundled model and CameraX: no Play services, but about 3 MB, and a camera screen of our own.
- **Camera permission:** the library's scan screen requests it at runtime, so the app asks only when the user taps *Scan QR code*.
- **Manifest:** the camera feature is declared with `required="false"`, so the app stays installable on devices without a camera.
- ZXing itself is only maintained, not developed further. Scanning one QR code is a stable, narrow use.

## Risks / Trade-offs

- [The desktop needs an inbound firewall exception.] → This is documented for the desktop. It is the price for the new flow.
- [The phone and a desktop with the old roles can no longer work together.] → The feature isn't released. The two sides must be updated together, and the desktop will reject connections on the wrong side.
- [A desktop with several interfaces shows an address the phone can't reach (e.g. WSL or VPN).] → The default-route tip in the protocol document. Otherwise the phone shows "Could not connect", and the desktop should let the user switch the interface.
- [Phones without a camera, or a denied camera permission.] → The user pastes or types the address the desktop shows as text.
- [The address has to be scanned or entered for every upload.] → This was your decision: nothing is remembered.
- [`connect()` blocks for up to 10 seconds before a Cancel takes effect.] → Acceptable. If needed later, the call can be made interruptible by closing the host.
