# Spec Delta

## ADDED Requirements

### Requirement: Connecting to the computer
When a peer-to-peer synchronization starts, the app SHALL show a dialog for the computer's address with a *Scan QR code* button, an editable field, a Cancel and a Connect button. The dialog SHALL be the same in all builds. The field SHALL be empty every time; the app SHALL NOT remember addresses.
- **Address:** a libp2p multiaddress `/ip4/<IPv4 address>/tcp/<port>/p2p/<computer peer ID>`: the IPv4 address as four decimal numbers from 0 to 255 (no host names), the port from 1 to 65535, and a valid peer ID, which is required.
- **Scan:** tapping *Scan QR code* SHALL open the camera scanner. If the scanned text is a valid address, the phone SHALL connect to it right away. Otherwise the dialog SHALL show the scanned text in the field with an error, and SHALL NOT connect.
- **Paste or type:** Connect SHALL be enabled when the field isn't empty. If the field holds a valid address, the phone SHALL connect to it; otherwise the dialog SHALL stay open and show an error at the field.

The phone SHALL dial exactly that one address over TCP, secured with Noise, and open one stream with the protocol ID `/relations/sync/1.0.0`. It SHALL accept only a connection whose Noise-authenticated peer ID is the one in the address. It SHALL NOT accept incoming connections. A connection attempt SHALL give up after 10 seconds. The phone's peer identity SHALL stay the same across synchronizations. Without a connection attempt, the dialog SHALL close after 2 minutes.

#### Scenario: Scan and connect
- **WHEN** the Relations desktop application shows its QR code and the user taps *Scan QR code* and scans it
- **THEN** the phone connects to that address without another tap and continues with the connection confirmation

#### Scenario: Paste the copied address
- **WHEN** the user pastes the address the desktop shows as text and taps Connect
- **THEN** the phone connects to it and continues with the connection confirmation

#### Scenario: Connect from the emulator to the host computer
- **WHEN** in the Android emulator, the desktop listens on port 47112 on the host computer, and the user enters `/ip4/10.0.2.2/tcp/47112/p2p/<the desktop's peer ID>` and taps Connect
- **THEN** the phone connects to the desktop without any port forwarding

#### Scenario: Scanned QR code is not an address
- **WHEN** the user scans a QR code that contains, for example, a web address
- **THEN** the field shows the scanned text with an error, and the phone doesn't connect

#### Scenario: Invalid address
- **WHEN** the user enters `/ip4/example.com/tcp/47112/p2p/12D3KooW…`, `/ip4/300.1.1.1/tcp/47112/p2p/12D3KooW…`, `/ip4/192.168.1.10/tcp/47112` (no peer ID) or `192.168.1.10:47112` and taps Connect
- **THEN** the dialog stays open, shows an error at the field, and the phone doesn't connect

#### Scenario: Nothing is remembered
- **WHEN** the user connected to a computer, and later starts another peer-to-peer synchronization
- **THEN** the field is empty

#### Scenario: Wrong peer ID
- **WHEN** the user enters the address of a running desktop with a peer ID that isn't the desktop's, and taps Connect
- **THEN** the phone doesn't accept the connection, and the synchronization ends with the message that it could not connect to the computer

#### Scenario: Computer cannot be reached
- **WHEN** the address doesn't accept a connection within 10 seconds
- **THEN** the synchronization ends with a message that the phone could not connect to the computer, and the local data is unchanged

#### Scenario: Stable identity
- **WHEN** the user runs two peer-to-peer synchronizations one after the other
- **THEN** the computer sees the same phone peer ID both times

#### Scenario: User cancels
- **WHEN** the dialog is open and the user taps Cancel
- **THEN** the dialog closes, and the local items, relations and search index are unchanged

#### Scenario: No address within the timeout
- **WHEN** the user doesn't connect within 2 minutes
- **THEN** the dialog closes with a message that no computer was selected, which reminds the user to start sending in the Relations desktop application and to scan the QR code it shows

## MODIFIED Requirements

### Requirement: Relations desktop as synchronization source
The app SHALL offer a synchronization source named "Relations desktop (WiFi)" (German: "Relations Desktop (WLAN)") in the cloud configuration dialog. It SHALL NOT require a token or connection string. It SHALL be selectable at any time, and selecting it SHALL make *Synchronize Database* receive the data from the desktop application instead of downloading it from a cloud provider.

#### Scenario: Select the Relations desktop source
- **WHEN** the user opens Settings, opens the cloud configuration dialog, selects "Relations desktop (WiFi)" and confirms
- **THEN** the selection is stored, and no token field is shown or required for this source

#### Scenario: Synchronize with the Relations desktop source
- **WHEN** "Relations desktop (WiFi)" is the stored source and the user chooses *Synchronize Database*
- **THEN** the app shows the same dialog as for the cloud providers, with the full and incremental switch, and starting it opens the dialog to scan or enter the computer's address instead of contacting a cloud service

### Requirement: WiFi prerequisite
A peer-to-peer synchronization SHALL start only when the phone has a local-network address on WiFi. The app SHALL request the camera permission only when the user taps *Scan QR code*, and the synchronization SHALL work without it by pasting or typing the address. The app SHALL NOT need Google Play services.

#### Scenario: Phone not on WiFi
- **WHEN** the user starts a peer-to-peer synchronization while the phone has no WiFi connection (for example, only mobile data)
- **THEN** the synchronization does not start, and the app asks the user to connect the phone to the same WiFi as the computer

#### Scenario: No permission prompt
- **WHEN** the user starts a peer-to-peer synchronization on WiFi for the first time
- **THEN** the app shows no permission request until the user taps *Scan QR code*

#### Scenario: Camera permission denied
- **WHEN** the user taps *Scan QR code* and denies the camera permission
- **THEN** the dialog stays open, and the user can paste or type the address

#### Scenario: Phone without Google Play services
- **WHEN** the user starts a peer-to-peer synchronization on WiFi on a phone without Google Play services
- **THEN** the synchronization works the same as on any other phone

### Requirement: Connection confirmation
When the phone has connected to a computer and received its `hello`, the app SHALL show the computer's name and a 6-digit confirmation code, and ask the user to accept or reject the connection. The Relations desktop application shows the same code.

The code SHALL be computed from the raw bytes of both peer IDs as follows: order the two byte arrays lexicographically as unsigned bytes and concatenate them, smaller first. Take the SHA-256 hash of the result, read its first 4 bytes as an unsigned big-endian integer, take that value modulo 1,000,000, and pad it with leading zeros to 6 digits.

Before the user accepts, the phone SHALL NOT send its request or receive any Relations data. The only message it reads is the computer's `hello`. The phone SHALL open at most one connection per synchronization. A rejected or unconfirmed connection SHALL be closed without exchanging Relations data.

#### Scenario: User accepts the connection
- **WHEN** the phone is connected and the user checks that the code matches the one on the computer and taps Accept
- **THEN** the transfer begins

#### Scenario: User rejects the connection
- **WHEN** the phone is connected and the user taps Reject
- **THEN** the phone closes the connection, no data is received, and the app shows that the synchronization was canceled

#### Scenario: Second connection
- **WHEN** the phone is connected to a computer and another device tries to connect to the phone or to open a second stream on the connection
- **THEN** the phone doesn't accept it, because it doesn't listen and handles only the one stream it opened

#### Scenario: Man in the middle
- **WHEN** a third device relays the connection between the phone and the computer
- **THEN** the codes shown on the phone and on the computer differ, because each side sees the third device's peer ID, so the user can reject the connection

### Requirement: Transfer protocol
The phone and the desktop application SHALL communicate over one libp2p stream with the protocol ID `/relations/sync/1.0.0`. The phone dials the desktop application and opens the stream.

Each control message SHALL be a frame: a 4-byte unsigned big-endian length, followed by that many bytes of UTF-8 JSON. The phone SHALL reject control frames larger than 65,536 bytes.

The messages SHALL be exchanged in this order:

1. When the stream is open, the desktop sends `{"type":"hello","protocol":1,"name":"<computer name>"}`.
2. After the user accepts, the phone sends `{"type":"request","protocol":1,"mode":"full"|"incremental"}`.
3. The desktop replies `{"type":"manifest","files":[{"name":"<file name>","size":<bytes>}, ...]}`. The files are listed in the order they must be applied, and file names SHALL be unique. For `full` the list contains exactly one file named `relations_all.zip`. For `incremental` it contains zero or more files whose names start with `relations_delta_`, oldest first.
4. The desktop sends the raw content of each listed file, one after the other in manifest order, each exactly `size` bytes long, with no framing between them.
5. After importing, the phone sends `{"type":"result","imported":["<file name>", ...]}` with the names of the files it imported successfully, or `{"type":"error","message":"<text>"}`, and then closes the stream.

The files SHALL have the same format as the corresponding files the desktop application uploads to the cloud providers. Instead of a manifest, the desktop MAY reply with `{"type":"error","message":"<text>"}`. The phone SHALL abort the synchronization in any of these cases:
- the `hello` announces a protocol version other than 1
- a message is malformed or arrives out of order
- a control frame is too large
- a manifest breaks the rules above
- the desktop sends more bytes than the manifest announced

#### Scenario: Protocol order for a full synchronization
- **WHEN** the user started a full synchronization, connected to the computer and accepted the connection
- **THEN** the phone sends a request with mode `full`, expects a manifest with exactly `relations_all.zip`, receives that file's bytes, and after the import answers with a result naming `relations_all.zip`

#### Scenario: Unsupported or malformed messages
- **WHEN** the desktop's `hello` announces protocol version 2, or a frame is not valid JSON, has an unknown `type`, or is larger than 65,536 bytes
- **THEN** the phone sends an error message, closes the connection, shows that the computer sent unexpected data, and leaves the local data unchanged

#### Scenario: More data than announced
- **WHEN** the desktop sends more bytes after the last announced file than the manifest's sizes add up to
- **THEN** the phone aborts as for a malformed message, and the local data is unchanged

#### Scenario: Desktop application reports an error
- **WHEN** the desktop replies `{"type":"error","message":"..."}` instead of a manifest
- **THEN** the phone closes the connection and shows the desktop's message

### Requirement: Peer-to-peer synchronization does not block the UI
Like a cloud synchronization, a running peer-to-peer synchronization SHALL keep the UI responsive, SHALL survive device rotation without starting a second synchronization, and SHALL NOT start while another synchronization of any source is running.

#### Scenario: Rotation while waiting or receiving
- **WHEN** the device is rotated while the connection dialog is open, the phone asks for confirmation, or receives files
- **THEN** the connection dialog (with the entered text), the confirmation dialog or the progress dialog is shown again after the rotation. The connection continues, and no second synchronization is started.

## REMOVED Requirements

### Requirement: Waiting for the computer
**Reason**: The roles are reversed. The phone no longer listens or announces itself, and it no longer waits for the desktop to dial.
**Migration**: Replaced by "Connecting to the computer". The desktop application has to listen and show its address as a QR code and as text, as described in `docs/peer-to-peer-protocol.md`.
