# Proposal

## Why

Today the phone listens and the Relations desktop application dials the phone. In practice the user starts on the desktop: the desktop shows what it offers, and the phone picks it up. The desktop shows its address as a QR code and as copyable text. Scanning is direct, works on networks that block multicast, and needs no discovery code. The current direction also makes testing awkward: in the emulator the phone can't be reached without `adb forward`, while an emulator can reach the computer directly at `10.0.2.2`.

## What Changes

- **BREAKING (protocol roles):**
  - The desktop listens and shows **one** connection address as a QR code and as copyable text. It doesn't announce itself.
  - The phone dials exactly that address and opens the `/relations/sync/1.0.0` stream.
  - The messages, their order (the desktop still sends `hello` first), the framing, the confirmation code and the file transfer stay the same.
  - The peer-to-peer feature isn't released yet, so the protocol ID and version stay unchanged.
- The phone no longer listens for connections and no longer shows a "waiting for the computer" dialog with its own address.
- A new **connection dialog** replaces the waiting dialog, the same in every build: *Scan QR code*, a field to paste or type the address (empty for every upload, nothing is remembered), and Cancel and Connect.
  - A valid scan connects right away.
  - An invalid scan puts the text in the field with an error.
  - Nothing is dialed until the field holds a valid address.
- The camera permission is requested only when the user taps *Scan*. Without it, pasting still works. No Google Play services are needed.
- New failure message when the phone can't connect to the computer. The "no computer" message is reworded for the new flow.
- The mDNS discovery that was implemented first on this branch is removed again: JmDNS, the multicast lock, the computer list, address ranking and the debug-only connection string. It was never released.
- The desktop side is **documented, not implemented** here. `docs/peer-to-peer-protocol.md` is rewritten for the new roles, including the QR code, the listening port, one connection at a time, and the firewall.
- The earlier idea of a debug dialog for the phone's *listen* address (`add-debug-p2p-listen-address`) was dropped, and its code was removed before this change.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `peer-to-peer-sync`:
  - The phone connects to a scanned or entered address instead of waiting. This replaces *Waiting for the computer* with *Connecting to the computer*.
  - *WiFi prerequisite* now applies to all builds, with the camera permission requested only when scanning.
  - *Connection confirmation* no longer has to handle additional incoming connections.
  - *Transfer protocol*: the stream is opened by the phone.
  - *Peer-to-peer synchronization does not block the UI* is reworded for the new dialog.

## Impact

- **Phone code:**
  - `p2p/PeerTransport.kt`: connect replaces listening, no discovery.
  - `p2p/Libp2pHost.kt`, `p2p/Libp2pTransport.kt`: dialing only.
  - `p2p/LocalAddress.kt`: `rank` is removed again, only the WiFi check stays.
  - New `p2p/PeerAddress.kt`: parses the address.
  - `cloud/PeerCloudProvider.kt`: waits for the address, then dials exactly once.
  - `cloud/SyncRunner.kt`: a `SelectComputer` state without a list, and the address as the user's answer.
  - `cloud/CloudSynchronize.kt`: the WiFi check in all builds.
  - `util/SyncObserver.kt`, `util/SelectComputerDialog.kt` and its layout: scan button and field.
  - `AndroidManifest.xml`, strings in `values` and `values-de`.
- **New dependency:** `com.journeyapps:zxing-android-embedded`.
- **Permissions:** `CAMERA` is added, requested only when scanning. `CHANGE_WIFI_MULTICAST_STATE` is removed, and `ACCESS_WIFI_STATE` too if nothing uses it anymore.
- **Tests:** `Libp2pHostTest` (the test plays a listening desktop), `PeerCloudProviderTest`, `FakePeerTransport`, `PeerAddressTest`, `LocalAddressTest`, `SyncRunnerTest`.
- **Docs:** `docs/peer-to-peer-protocol.md`.
- **Desktop application (separate repository):** has to listen, show one address as a QR code and as text, and answer the stream. It has to be adapted before the two work together again.
