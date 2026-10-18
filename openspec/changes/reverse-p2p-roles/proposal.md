# Proposal

## Why

Today the phone listens and the Relations desktop application dials the phone. In practice the user starts on the desktop: the desktop shows what it offers, and the phone should pick it up. The current direction also makes testing awkward. In the emulator the phone can't be reached without `adb forward`, and the forwarded port collides with the desktop's own port (`PORT_UNAVAILABLE`). An emulator, on the other hand, can reach the computer directly at `10.0.2.2`.

## What Changes

- **BREAKING (protocol roles):**
  - The desktop listens, announces itself through libp2p mDNS under a Relations-specific service name, and shows its connection address(es).
  - The phone discovers desktops, lists them, dials the selected one and opens the `/relations/sync/1.0.0` stream.
  - The messages, their order (the desktop still sends `hello` first), the framing, the confirmation code and the file transfer stay the same.
  - The peer-to-peer feature isn't released yet, so the protocol ID and version stay unchanged.
- The phone no longer listens for connections and no longer shows a "waiting for the computer" dialog with its own address.
- A new **computer selection dialog** replaces the waiting dialog. It shows the computers found on the local network while the search runs, for up to 2 minutes. The user picks one and taps Connect, or taps Cancel.
  - **Release builds:** the list only.
  - **Debug builds:** additionally an editable connection string (`/ip4/<address>/tcp/<port>/p2p/<desktop peer ID>`). It is prefilled with the selected computer's address, or with the last string used, so a developer can connect to any address, e.g. `/ip4/10.0.2.2/tcp/47112/p2p/…` from the emulator. In debug builds the WiFi prerequisite doesn't apply.
- New failure message when the phone can't connect to the selected computer. The "no computer found" message is reworded for the new direction.
- The desktop side is **documented, not implemented** here. `docs/peer-to-peer-protocol.md` is rewritten for the new roles, including discovery, the listening port, one connection at a time, and the firewall.
- The earlier idea of a debug dialog for the phone's *listen* address (`add-debug-p2p-listen-address`) was dropped, and its code was removed before this change. The connection string replaces it.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `peer-to-peer-sync`:
  - The phone discovers and connects instead of waiting. This replaces *Waiting for the computer* with *Finding and connecting to the computer*.
  - *WiFi prerequisite* now makes an exception for debug builds.
  - *Connection confirmation* no longer has to handle additional incoming connections.
  - *Transfer protocol*: the stream is opened by the phone.
  - *Transfer failures* and *Peer-to-peer synchronization does not block the UI* are reworded for the new direction.

## Impact

- **Phone code:**
  - `p2p/PeerTransport.kt`: discovery and connect replace listening.
  - `p2p/Libp2pHost.kt`: the dialing role, and mDNS as a query.
  - `p2p/Libp2pTransport.kt`
  - New `p2p/PeerAddress.kt`: parses the connection string.
  - `cloud/PeerCloudProvider.kt`: a search phase, then connecting.
  - `cloud/SyncRunner.kt`: a new `SyncState` and the user's choice as an answer.
  - `cloud/CloudSynchronize.kt`: the WiFi check applies to release builds only.
  - `util/SyncObserver.kt`: renders the new state.
  - New `util/SelectComputerDialog.kt` and its layout.
  - Strings in `values` and `values-de`.
- **Tests:** `Libp2pHostTest` (the test plays a listening desktop), `PeerCloudProviderTest`, `FakePeerTransport`, new `PeerAddressTest`, `SyncRunnerTest`.
- **Docs:** `docs/peer-to-peer-protocol.md`.
- **Desktop application (separate repository):** has to listen, announce itself, show its address and answer the stream. It has to be adapted before the two work together again.
- No new dependencies. The manifest permissions are unchanged: `CHANGE_WIFI_MULTICAST_STATE` is still needed for mDNS.
