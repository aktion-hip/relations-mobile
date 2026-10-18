# Spec Delta

## ADDED Requirements

### Requirement: Finding and connecting to the computer
When a peer-to-peer synchronization starts, the phone SHALL search the local network for Relations desktop applications through libp2p mDNS discovery, with the service name `_relations-sync._udp`. It SHALL NOT accept incoming connections. The phone's peer identity SHALL stay the same across synchronizations.

While it searches, the app SHALL show a dialog listing the computers found so far. Each computer SHALL be listed once, identified by its address and the last 6 characters of its peer ID. The list SHALL be updated as computers are found. The dialog SHALL have a Connect button, enabled only when a computer is selected, and a Cancel button.

When the user taps Connect, the phone SHALL stop searching and dial the selected computer. The phone connects over TCP, secured with Noise, and opens one stream with the protocol ID `/relations/sync/1.0.0`. If the computer announced several IPv4 addresses, the phone SHALL try them one after the other, and SHALL try those in the phone's own subnet first, until one connection succeeds. The phone SHALL accept only a connection whose Noise-authenticated peer ID is the selected computer's peer ID.

The phone SHALL stop searching when the user cancels, or after 2 minutes without a selection. Each connection attempt SHALL give up after 10 seconds.

#### Scenario: Computer found and selected
- **WHEN** the Relations desktop application is ready to send on the same WiFi and the user starts a peer-to-peer synchronization
- **THEN** the dialog lists the computer with its address and the end of its peer ID, and after the user selects it and taps Connect, the phone connects and continues with the connection confirmation

#### Scenario: Several computers
- **WHEN** two computers on the WiFi are ready to send
- **THEN** both are listed, Connect stays disabled until the user selects one, and the phone connects only to the selected one

#### Scenario: Other libp2p devices are not listed
- **WHEN** another libp2p application on the network announces itself through mDNS with a different service name
- **THEN** it doesn't appear in the list

#### Scenario: Computer cannot be reached
- **WHEN** the user selects a computer and none of its addresses accept a connection within 10 seconds each
- **THEN** the synchronization ends with a message that the phone could not connect to the computer, and the local data is unchanged

#### Scenario: Stable identity
- **WHEN** the user runs two peer-to-peer synchronizations one after the other
- **THEN** the computer sees the same phone peer ID both times

#### Scenario: User cancels the search
- **WHEN** the phone is searching and the user taps Cancel
- **THEN** the phone stops searching, the dialog closes, and the local items, relations and search index are unchanged

#### Scenario: No computer selected within the timeout
- **WHEN** the user doesn't connect to a computer within 2 minutes
- **THEN** the phone stops searching and shows a message that no computer was found, which reminds the user to start sending in the Relations desktop application first and to use the same WiFi

### Requirement: Connection string in debug builds
In debug builds, the computer selection dialog SHALL also show an editable connection string: a libp2p multiaddress in the form `/ip4/<IPv4 address>/tcp/<port>/p2p/<computer peer ID>`, for example `/ip4/10.0.2.2/tcp/47112/p2p/12D3KooW…`.
- **Accepted values:** the IPv4 address as four decimal numbers from 0 to 255 (no host names), the port from 1 to 65535, and a peer ID that is required.
- **Prefill:** the field is prefilled with the last connection string used in a debug build, if any. Selecting a computer in the list SHALL replace the field's content with that computer's preferred address.
- **Connect:** in debug builds, Connect SHALL be enabled whenever the field isn't empty, and it SHALL dial exactly the address in the field. There's no fallback to other addresses.
- **Invalid entry:** if the field doesn't match the format when the user taps Connect, the dialog SHALL stay open and show an error at the field.
- **Remembering:** a connection string used for connecting SHALL be remembered for the next time.

Release builds SHALL NOT show the connection string.

#### Scenario: Release build shows only the list
- **WHEN** a user of a release build starts a peer-to-peer synchronization
- **THEN** the dialog shows only the list of found computers, and Connect is enabled only after selecting one

#### Scenario: Connect from the emulator to the host computer
- **WHEN** in a debug build running in the Android emulator, the desktop listens on port 47112 on the host computer, and the user enters `/ip4/10.0.2.2/tcp/47112/p2p/<the desktop's peer ID>` and taps Connect
- **THEN** the phone connects to the desktop and continues with the connection confirmation, without any port forwarding

#### Scenario: Selecting a computer fills the field
- **WHEN** in a debug build the list shows a computer and the user selects it
- **THEN** the field shows `/ip4/<that computer's preferred address>/tcp/<its port>/p2p/<its peer ID>` and can still be edited before tapping Connect

#### Scenario: Wrong peer ID
- **WHEN** in a debug build the user enters the address of a running desktop with a peer ID that isn't the desktop's, and taps Connect
- **THEN** the phone doesn't accept the connection, and the synchronization ends with the message that it could not connect to the computer

#### Scenario: Invalid connection string
- **WHEN** in a debug build the user enters `/ip4/example.com/tcp/47112/p2p/12D3KooW…`, `/ip4/300.1.1.1/tcp/47112/p2p/12D3KooW…`, `/ip4/192.168.1.10/tcp/47112` (no peer ID) or `192.168.1.10:47112` and taps Connect
- **THEN** the dialog stays open, shows an error at the field, and the phone doesn't connect

#### Scenario: Last connection string is remembered
- **WHEN** in a debug build the user connected with a connection string, and later starts another peer-to-peer synchronization
- **THEN** the field is prefilled with that connection string

## MODIFIED Requirements

### Requirement: Relations desktop as synchronization source
The app SHALL offer a synchronization source named "Relations desktop (WiFi)" (German: "Relations Desktop (WLAN)") in the cloud configuration dialog. It SHALL NOT require a token or connection string. It SHALL be selectable at any time, and selecting it SHALL make *Synchronize Database* receive the data from the desktop application instead of downloading it from a cloud provider.

#### Scenario: Select the Relations desktop source
- **WHEN** the user opens Settings, opens the cloud configuration dialog, selects "Relations desktop (WiFi)" and confirms
- **THEN** the selection is stored, and no token field is shown or required for this source

#### Scenario: Synchronize with the Relations desktop source
- **WHEN** "Relations desktop (WiFi)" is the stored source and the user chooses *Synchronize Database*
- **THEN** the app shows the same dialog as for the cloud providers, with the full and incremental switch, and starting it begins searching for the computer instead of contacting a cloud service

### Requirement: WiFi prerequisite
In release builds, a peer-to-peer synchronization SHALL start only when the phone has a local-network address on WiFi. Debug builds SHALL start it without that check, so that a connection string can be used (see "Connection string in debug builds"). The app SHALL NOT need runtime permissions or Google Play services for it.

#### Scenario: Phone not on WiFi
- **WHEN** the user of a release build starts a peer-to-peer synchronization while the phone has no WiFi connection (for example, only mobile data)
- **THEN** the synchronization does not start, and the app asks the user to connect the phone to the same WiFi as the computer

#### Scenario: No permission prompt
- **WHEN** the user starts a peer-to-peer synchronization on WiFi for the first time
- **THEN** the app shows no permission request and starts searching for the computer

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
- **WHEN** the device is rotated while the phone searches for computers, asks for confirmation, or receives files
- **THEN** the selection dialog (with the computers found so far, and in debug builds the entered connection string), the confirmation dialog or the progress dialog is shown again after the rotation. The search or connection continues, and no second synchronization is started.

## REMOVED Requirements

### Requirement: Waiting for the computer
**Reason**: The roles are reversed. The phone no longer listens or announces itself, and it no longer waits for the desktop to dial.
**Migration**: Replaced by "Finding and connecting to the computer" (and "Connection string in debug builds"). The desktop application has to listen and announce itself as described in `docs/peer-to-peer-protocol.md`.
