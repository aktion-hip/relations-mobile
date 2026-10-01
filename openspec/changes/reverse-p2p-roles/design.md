# Design

## Context

State of the working tree: the committed peer-to-peer implementation (commit `9cf08ec`). The dropped `add-debug-p2p-listen-address` change was removed together with its code, so there is no listen-address dialog or parser to build on.

Current flow, from the archived peer-to-peer design:
- `PeerCloudProvider.synchronize()` calls `transport.start(port)` and publishes `SyncState.WaitingForPeer(address)`, which `SyncObserver` renders in the `ProgressDialog`. It then waits for `PeerEvent.Connected` and runs HELLO → CONFIRM → MANIFEST → FILES on an event queue.
- `Libp2pHost` accepts streams as the responder only. `onStartInitiator` rejects them with "This host does not dial". It announces itself with `MDnsDiscovery(host, address = bindAddress)`, which uses the default service tag `_ipfs-discovery._udp`. The protocol document says `_p2p._udp.local`, which is wrong.
- `PeerAnswer` is an enum (`ACCEPT`, `REJECT`, `CANCEL`). `ConfirmPeerDialog` is a `DialogFragment` that `SyncObserver` shows by tag, so it survives rotation.

jvm-libp2p 1.3.7 `MDnsDiscovery`:
- constructor `(host, serviceTag, queryInterval, address)`
- `start()` registers a service built from `host.listenAddresses()` *and* adds an answer listener for queries
- found peers arrive as `PeerInfo(peerId, addresses)`, with no name or TXT data

## Goals / Non-Goals

**Goals:**
- Keep the whole interactive phase (searching, choosing, confirming) inside the `SyncRunner` session, so that it survives rotation like the rest.
- Reuse the protocol layer unchanged: `PeerProtocol`, `StreamDecoder`, the confirmation code, and the provider's HELLO → FILES phases.
- Keep the transport testable with `FakePeerTransport`, and keep an end-to-end JVM test in which the test plays a *listening* desktop.

**Non-Goals:**
- Implementing the desktop side. It is only documented, in `docs/peer-to-peer-protocol.md`.
- QR codes, remembered or trusted computers, relays, or IPv6.
- Keeping the phone-listens mode as an alternative.

## Decisions

### 1. The desktop listens, the phone dials. This reverses the archived Decision 1
The phone runs a jvm-libp2p host with no listen address (TCP, Noise, mplex, the same persisted Ed25519 identity). It dials `/ip4/<a>/tcp/<p>/p2p/<id>` with `StrictProtocolBinding.dial(host, multiaddr)`, and becomes the *initiator* of `/relations/sync/1.0.0`. The protocol handler's roles swap: `onStartInitiator` pushes the `StreamHandler`, and `onStartResponder` closes the stream ("This host does not listen").
*Why this is acceptable now:* the archived decision chose "the phone listens" because desktop firewalls block incoming connections. The desktop now has to ask for a firewall exception once (see the protocol document). In return the user flow starts on the desktop, and emulators can reach the host at `10.0.2.2`.
*Alternative:* support both directions. It was rejected because it doubles the UI and the tests for a feature that isn't released yet.

### 2. mDNS with a Relations service tag, as a query from a host that doesn't listen
Both sides use `MDnsDiscovery(host, serviceTag = "_relations-sync._udp.local.", address = <WiFi IPv4>)` on the desktop. The desktop announces its listen addresses, and the phone only collects `PeerInfo`s through `newPeerFoundListeners`. The custom tag keeps IPFS nodes and other libp2p peers out of the list.
*Spike outcome (task 1.1):* the fallback is needed and works. The desktop keeps using `MDnsDiscovery` with the tag `_relations-sync._udp.local.` (the domain is required). The phone uses `JmDNS.create(address)` + `addAnswerListener` and parses the records itself: the TXT text is one length byte plus the base58 peer ID, SRV gives the port and A gives the address. This is because jvm-libp2p's listener mishandles 52-character Ed25519 IDs.
*Original risk:* `start()` also registers a service built from `host.listenAddresses()`, which is empty on the phone. Task 1.1 is a spike that checks whether this works (registering nothing, or throwing). If it throws, the fallback is to use the bundled `io.libp2p.discovery.mdns.JmDNS` directly: `JmDNS.create(address)` plus `addAnswerListener(serviceTagLocal, queryInterval, listener)`, without `registerService`, and to parse the answers the way `MDnsDiscovery.Companion.Listener` does. That stays inside `Libp2pHost`, so the plan is otherwise unchanged.
The multicast lock stays in place, and is held while searching instead of while listening.

### 3. Transport interface
```kotlin
interface PeerTransport {
    val localPeerId: ByteArray
    fun setListener(listener: ((PeerEvent) -> Unit)?)
    fun startDiscovery()                 // PeerEvent.Found(peerId: String, addresses: List<String>) as peers appear
    fun stopDiscovery()
    fun connect(address: String): String // blocking, <= 10 s; returns the connection id, Connected is emitted as before
    fun send(connectionId: String, bytes: ByteArray)
    fun close(connectionId: String)
    fun stop()
}
```
- `start(port, strictPort)` and `stopListening()` are removed.
- `connect()` throws if dialing, Noise, or protocol negotiation fails, and when the remote peer ID doesn't match. libp2p checks the peer ID during the Noise handshake against the `/p2p/` part.
- `PeerEvent.Connected` keeps carrying the remote peer ID for the confirmation code.

### 4. The search phase in the provider, and the user's choice as an answer
- `SyncState.WaitingForPeer(address)` becomes `SyncState.SelectComputer(computers: List<FoundComputer>, editable: Boolean)`, with `data class FoundComputer(peerId: String, addresses: List<String>, preferred: String)`. Here `preferred` is the full `/ip4/…/tcp/…/p2p/…` string.
- `PeerAnswer` becomes a sealed class: `Accept`, `Reject`, `Cancel`, and `Connect(address: String?, computer: String?)`. `SyncRunner.answerPeer(Boolean)` stays, and `SyncRunner.connectTo(...)` is added. `Connect` is only accepted while the state is `SelectComputer`.
- In `PeerCloudProvider.synchronize()`:
  1. `startDiscovery()`
  2. publish an empty list
  3. on each `Found`, merge by peer ID and republish
  4. until `Connect`, `Cancel`, or 2 minutes (`NO_COMPUTER`)
  5. `stopDiscovery()`
  6. connect (next paragraph)
  7. the existing `awaitHello` and the rest

  The connect-to-computer event path replaces `awaitConnection()`.
- **Connecting:**
  - list selection: try each of the computer's IPv4 addresses (Decision 5 gives the order) until one succeeds
  - debug string: try exactly that address
  - all attempts fail: `PeerMessage.CONNECT_FAILED`, with a new string `p2p_connect_failed`

  `connect()` blocks the provider thread for at most 10 seconds per address. A Cancel that arrives meanwhile is handled right after the attempt returns.
- `editable` is `BuildConfig.DEBUG`, passed in through `PeerCloudProvider.create`, so the provider stays free of Android classes.

### 5. Choosing the address of a found computer
`FoundComputer.preferred` and the order of the attempts come from the IPv4 `/tcp/` addresses in `PeerInfo.addresses`. Loopback and link-local addresses are dropped. The order is:
1. addresses in the phone's WiFi subnet. `LocalAddress` currently reads only `inetAddresses`, so it is extended to read `interfaceAddresses`, which carry the network prefix length. `LocalAddress.current()` then returns the address together with its prefix length.
2. other site-local addresses
3. the rest

This is a pure function next to `LocalAddress` (`LocalAddress.rank(addresses, local)`), so unit tests can cover it.
*Why:* Windows desktops often announce Hyper-V, WSL or VPN adapters (`172.x`) next to the WiFi or LAN address.

### 6. `SelectComputerDialog` (DialogFragment)
It is rendered by `SyncObserver` for `SelectComputer`, like `ConfirmPeerDialog`.
- **Layout:** a `ListView` with single choice, showing `address · …<last 6 of peer ID>`; an empty-state text with a spinner ("Searching…"); in debug builds, an `EditText` below the list; and Connect and Cancel buttons.
- **Updates:** `SyncObserver` calls `dialog.update(computers)` on each new state instead of recreating the dialog, so the selection and the edited text are kept. After rotation, the fragment restores the selection and the text from its saved state.
- **Connect:**
  - Release builds: `SyncRunner.connectTo(computer = peerId)`.
  - Debug builds: the parsed field text. The positive button is overridden in `setOnShowListener` so that an invalid entry keeps the dialog open.
  - Debug text is stored under `p2pDebugConnectAddress` in the default `SharedPreferences` and prefilled from there.
- The dialog lives in the main source set, gated by `BuildConfig.DEBUG`. A `src/debug` source set would still need a hook in the main code.

### 7. Connection-string parser
New pure-Kotlin `p2p/PeerAddress.kt`: `data class PeerAddress(address: Inet4Address, port: Int, peerId: String)` with `parse(text): PeerAddress?`.
- It matches `^/ip4/(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/tcp/(\d{1,5})/p2p/([1-9A-HJ-NP-Za-km-z]+)/?$` after `trim()`, and checks that each octet is 0–255 and the port is 1–65535.
- The peer ID must decode with `PeerId.fromBase58`.
- The address is built with `InetAddress.getByAddress`, so the main thread does no DNS lookup.
- `toString()` gives the normalized multiaddress.

*Alternative:* `Multiaddr(text)`. It accepts many more protocols, which would then have to be rejected one by one.


### 8. Protocol document for the desktop
`docs/peer-to-peer-protocol.md` is rewritten as the desktop contract. It covers:
- the parameters table: the desktop listens on 47112, or on a port the system chooses if 47112 is taken, and announces itself with the service tag `_relations-sync._udp`
- "Offering the data": the desktop shows its multiaddresses and peer ID, with the last 6 characters highlighted so they can be matched against the phone's list
- the desktop accepts one stream and rejects other connections while a synchronization runs
- the desktop sends `hello` as soon as the stream opens, and shows the code
- the firewall: a Windows Defender prompt for incoming TCP, and what the desktop should tell the user
- the emulator hint (`10.0.2.2`)
- the unchanged framing, sequence, confirmation code and invalid messages

## Risks / Trade-offs

- [mDNS registration may fail on a host that doesn't listen.] → The spike in task 1.1 checks it, with a JmDNS fallback (Decision 2).
- [Release builds have no manual fallback. Networks that block multicast (guest WiFi, some corporate networks) can't synchronize.] → This was your decision. The "no computer found" message says so, and the protocol document tells desktop implementers about it. A manual entry for release builds can be added later without changing the protocol.
- [The desktop needs an inbound firewall exception.] → This is documented for the desktop. It is the price for the new flow.
- [The phone and a desktop with the old roles can no longer work together.] → The feature isn't released. The two sides must be updated together, and the desktop will reject connections on the wrong side.
- [The list shows no computer name, because mDNS carries none.] → It shows the address and the end of the peer ID, and the desktop shows the same. The name appears in the confirmation dialog.
- [`connect()` blocks for up to 10 seconds per address before a Cancel takes effect.] → Acceptable. If needed later, the call can be made interruptible by closing the host.
