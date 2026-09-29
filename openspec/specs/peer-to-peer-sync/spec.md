# peer-to-peer-sync Specification

## Purpose

Defines how RelationsMobile receives the Relations data directly from the Relations desktop application on the same local network, using a libp2p peer-to-peer connection instead of a cloud provider. It also defines the transfer protocol the desktop peer must implement.

## Requirements

### Requirement: Relations desktop as synchronization source
The app SHALL offer a synchronization source named "Relations desktop (WiFi)" (German: "Relations Desktop (WLAN)") in the cloud configuration dialog. It SHALL NOT require a token or connection string. It SHALL be selectable at any time, and selecting it SHALL make *Synchronize Database* receive the data from the desktop application instead of downloading it from a cloud provider.

#### Scenario: Select the Relations desktop source
- **WHEN** the user opens Settings, opens the cloud configuration dialog, selects "Relations desktop (WiFi)" and confirms
- **THEN** the selection is stored, and no token field is shown or required for this source

#### Scenario: Synchronize with the Relations desktop source
- **WHEN** "Relations desktop (WiFi)" is the stored source and the user chooses *Synchronize Database*
- **THEN** the app shows the same dialog as for the cloud providers, with the full and incremental switch, and starting it begins waiting for the computer instead of contacting a cloud service

### Requirement: WiFi prerequisite
A peer-to-peer synchronization SHALL start only when the phone has a local-network address on WiFi. The app SHALL NOT need runtime permissions or Google Play services for it.

#### Scenario: Phone not on WiFi
- **WHEN** the user starts a peer-to-peer synchronization while the phone has no WiFi connection (for example, only mobile data)
- **THEN** the synchronization does not start, and the app asks the user to connect the phone to the same WiFi as the computer

#### Scenario: No permission prompt
- **WHEN** the user starts a peer-to-peer synchronization on WiFi for the first time
- **THEN** the app shows no permission request and starts waiting for the computer

#### Scenario: Phone without Google Play services
- **WHEN** the user starts a peer-to-peer synchronization on WiFi on a phone without Google Play services
- **THEN** the synchronization works the same as on any other phone

### Requirement: Waiting for the computer
When a peer-to-peer synchronization starts, the phone SHALL accept libp2p connections over TCP on its WiFi address, secured with Noise, for the protocol ID `/relations/sync/1.0.0`. It SHALL announce itself on the local network through libp2p mDNS discovery. The phone's peer identity SHALL stay the same across synchronizations.

While it waits, the app SHALL show a dialog saying that it is waiting for the Relations desktop application. The dialog SHALL show the phone's address for manual entry on the computer (IP address, TCP port and peer ID, as a libp2p multiaddress such as `/ip4/192.168.1.23/tcp/47112/p2p/12D3KooW…`), and it SHALL have a Cancel button. The phone SHALL stop accepting connections and stop announcing itself once a computer is connected, when the user cancels, or after 2 minutes without a connection.

#### Scenario: Computer finds the phone through mDNS
- **WHEN** the phone is waiting and the Relations desktop application on the same WiFi discovers it through mDNS and connects
- **THEN** the phone stops announcing itself and continues with the connection confirmation

#### Scenario: Computer connects with the manual address
- **WHEN** the network blocks mDNS and the user enters the address shown on the phone into the Relations desktop application
- **THEN** the computer connects to the phone, and the synchronization continues with the connection confirmation

#### Scenario: Stable identity
- **WHEN** the user runs two peer-to-peer synchronizations one after the other
- **THEN** the peer ID shown in the waiting dialog is the same both times

#### Scenario: User cancels waiting
- **WHEN** the phone is waiting and the user taps Cancel
- **THEN** the phone stops accepting connections and announcing itself, the dialog closes, and the local items, relations and search index are unchanged

#### Scenario: No computer within the timeout
- **WHEN** no computer connects within 2 minutes
- **THEN** the phone stops accepting connections and announcing itself, and shows a message that no computer was found, which reminds the user to start sending in the Relations desktop application, to use the same WiFi, and to enter the shown address if the computer does not find the phone

### Requirement: Connection confirmation
When a computer connects, the app SHALL show the computer's name and a 6-digit confirmation code, and ask the user to accept or reject the connection. The Relations desktop application shows the same code.

The code SHALL be computed from the raw bytes of both peer IDs as follows: order the two byte arrays lexicographically as unsigned bytes and concatenate them, smaller first. Take the SHA-256 hash of the result, read its first 4 bytes as an unsigned big-endian integer, take that value modulo 1,000,000, and pad it with leading zeros to 6 digits.

Before the user accepts, the phone SHALL NOT send its request or receive any Relations data. The only message it reads is the computer's `hello`. The app SHALL accept at most one connection per synchronization. Rejected, additional or unconfirmed connections SHALL be closed without exchanging Relations data.

#### Scenario: User accepts the connection
- **WHEN** a computer connects and the user checks that the code matches the one on the computer and taps Accept
- **THEN** the transfer begins

#### Scenario: User rejects the connection
- **WHEN** a computer connects and the user taps Reject
- **THEN** the phone closes the connection, no data is received, and the app shows that the synchronization was canceled

#### Scenario: Second connection
- **WHEN** a connection is already pending or accepted and another computer connects
- **THEN** the second connection is closed without asking the user

#### Scenario: Man in the middle
- **WHEN** a third device relays the connection between the computer and the phone
- **THEN** the codes shown on the phone and on the computer differ, because the phone sees the third device's peer ID, so the user can reject the connection

### Requirement: Transfer protocol
The phone and the desktop application SHALL communicate over one libp2p stream with the protocol ID `/relations/sync/1.0.0`, opened by the desktop application.

Each control message SHALL be a frame: a 4-byte unsigned big-endian length, followed by that many bytes of UTF-8 JSON. The phone SHALL reject control frames larger than 65,536 bytes.

The messages SHALL be exchanged in this order:

1. The desktop sends `{"type":"hello","protocol":1,"name":"<computer name>"}`.
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
- **WHEN** the user started a full synchronization and accepted the connection
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

### Requirement: Full synchronization from the computer
For a full peer-to-peer synchronization the app SHALL replace the local items and relations with the received data and rebuild the search index, just as a full cloud synchronization does.

#### Scenario: Successful full synchronization
- **WHEN** the desktop sends a valid `relations_all.zip`
- **THEN** the app replaces the local items and relations, rebuilds the search index, shows import progress, reports success, and shows the synchronized data

### Requirement: Incremental synchronization from the computer
For an incremental peer-to-peer synchronization the app SHALL apply every received delta file in manifest order to the local items and relations and update the search index. If the manifest lists no files, the app SHALL report that no incremental data was found and set the synchronization switch to full for the next attempt, just as the cloud providers do.

#### Scenario: Several deltas applied in order
- **WHEN** the desktop announces and sends three `relations_delta_*.zip` files
- **THEN** the app applies them in manifest order, updates the search index, reports success, and the `result` message sent to the desktop names all three files

#### Scenario: No deltas available
- **WHEN** the desktop answers an incremental request with an empty manifest
- **THEN** the app shows that no incremental data was found, sets the switch to full, and leaves the local data unchanged

### Requirement: Transfer failures leave local data unchanged
The app SHALL start importing only after it has fully received every file listed in the manifest. If the connection is lost, the user cancels, or no bytes arrive for 60 seconds before all files are received, the app SHALL abort, discard the received files, and leave the local items, relations and search index as they were.

#### Scenario: Connection lost during transfer
- **WHEN** the WiFi connection drops or the desktop application is closed while a file is being received
- **THEN** the app shows that the connection to the computer was lost, deletes the partially received files, and the local data is unchanged

#### Scenario: Transfer stalls
- **WHEN** no bytes arrive for 60 seconds while files are still expected
- **THEN** the app aborts as in a lost connection and tells the user the transfer timed out

#### Scenario: User cancels during transfer
- **WHEN** the user taps Cancel while files are still being received
- **THEN** the app closes the connection, discards the received files, and the local data is unchanged

### Requirement: Peer-to-peer synchronization does not block the UI
Like a cloud synchronization, a running peer-to-peer synchronization SHALL keep the UI responsive, SHALL survive device rotation without starting a second synchronization, and SHALL NOT start while another synchronization of any source is running.

#### Scenario: Rotation while waiting or receiving
- **WHEN** the device is rotated while the phone waits for the computer, asks for confirmation, or receives files
- **THEN** the waiting, confirmation or progress dialog is shown again after the rotation, the connection continues, and no second synchronization is started
