# Relations peer-to-peer synchronization protocol

This document describes how the Relations desktop application sends its data to RelationsMobile on the same local network. It is written for the people who implement the desktop side.

The connection uses [libp2p](https://libp2p.io). The phone uses [jvm-libp2p](https://github.com/libp2p/jvm-libp2p), and the desktop can use the same library on the JVM (`io.libp2p:jvm-libp2p:1.3.7-RELEASE`, from the Cloudsmith repository `https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/`).

## Parameters

| Parameter | Value |
|-----------|-------|
| Transport | TCP (IPv4) |
| Security | Noise |
| Stream multiplexer | mplex |
| Protocol ID | `/relations/sync/1.0.0` |
| Protocol version | `1` |
| Desktop role | Listens on port `47112`, or on a port chosen by the system if 47112 is taken. Announces itself through libp2p mDNS with the service tag `_relations-sync._udp.local.`. |
| Phone role | Searches through mDNS, dials the desktop and opens the stream. It doesn't listen. |
| Maximum control frame | 65,536 bytes of JSON |

## Offering the data

1. The user starts sending in the desktop application. The desktop creates (or loads) its libp2p identity, starts listening, and announces itself through mDNS. With jvm-libp2p, this is `MDnsDiscovery(host, "_relations-sync._udp.local.", queryInterval, <address of the LAN or WiFi interface>)`. The service tag must include the `.local.` domain. The desktop should keep its identity across sessions, so that its peer ID stays the same.
2. The desktop shows its peer ID and the multiaddresses it listens on, e.g. `/ip4/192.168.1.10/tcp/47112/p2p/12D3KooW…`. It should highlight the **last 6 characters of the peer ID**, because the phone lists each computer as `<address>:<port> · …<last 6 characters>`.
3. The desktop accepts **one stream per synchronization**. While a synchronization runs, it rejects other connections and streams.

## Finding the computer

1. The user selects *Relations desktop (WiFi)* in the phone's cloud configuration, chooses *Synchronize Database*, and picks full or incremental. The phone searches through mDNS for up to **120 seconds** and lists the computers it finds.
2. The user selects the computer and taps *Connect*. The phone tries the computer's announced IPv4 addresses one after the other, addresses in its own subnet first, each for up to **10 seconds**. It accepts only a connection whose Noise-authenticated peer ID is the announced one.
3. The phone opens a stream with the protocol ID `/relations/sync/1.0.0`. It is the only stream the phone opens on the connection. The phone rejects streams the desktop opens.

Debug builds of the app also let the developer enter the connection string, e.g. `/ip4/10.0.2.2/tcp/47112/p2p/12D3KooW…`, and dial exactly that address.

### Firewall

The desktop has to accept incoming TCP connections on its listening port. On Windows, starting to listen triggers a Windows Defender Firewall prompt the first time. The desktop should tell the user to allow access on private networks. If the user denies it, the phone lists the computer but can't connect, and it shows "Could not connect to the computer".

### Android emulator

In the Android emulator, `10.0.2.2` is the host computer's loopback address, and mDNS doesn't reach the host. To test with a desktop running on the host, use a debug build and enter `/ip4/10.0.2.2/tcp/<desktop port>/p2p/<desktop peer ID>`. No port forwarding is needed.

## Framing

Every control message is a frame: a 4-byte unsigned big-endian length, followed by that many bytes of UTF-8 JSON. The phone rejects frames with more than 65,536 bytes of JSON.

The file contents (step 4 below) are sent as raw bytes without framing.

## Sequence

1. As soon as the phone has opened the stream, the desktop sends **hello**. `name` is the computer name the phone displays.

   ```json
   {"type":"hello","protocol":1,"name":"Office PC"}
   ```

2. Both sides show the **confirmation code** (see below), and the user accepts the connection on the phone. The desktop should also show the code. After the user accepts, the phone sends its **request**:

   ```json
   {"type":"request","protocol":1,"mode":"full"}
   ```

   `mode` is `full` or `incremental`. If the user rejects the connection, the phone closes the stream without sending anything.

3. The desktop answers with a **manifest**. It lists the files it is going to send, with their exact sizes in bytes, in the order the phone must apply them. File names must be unique.

   For a full synchronization, the manifest contains exactly one file, `relations_all.zip`:

   ```json
   {"type":"manifest","files":[{"name":"relations_all.zip","size":1048576}]}
   ```

   For an incremental synchronization, it contains zero or more files whose names start with `relations_delta_`, oldest first:

   ```json
   {"type":"manifest","files":[{"name":"relations_delta_20260901.zip","size":2048},{"name":"relations_delta_20260915.zip","size":4096}]}
   ```

   An empty list means there are no increments. The phone then tells the user to run a full synchronization:

   ```json
   {"type":"manifest","files":[]}
   ```

   If the desktop can't provide the data, it sends an **error** instead. The phone shows the message and closes the stream:

   ```json
   {"type":"error","message":"No Relations export available."}
   ```

4. Right after the manifest, the desktop writes the raw content of each listed file, one after the other in manifest order, each exactly `size` bytes long. The file contents are the same ZIP files the desktop uploads to Dropbox or Microsoft Azure. After the last file, the desktop sends nothing more until the phone answers.
5. The phone receives *all* files before it imports any of them. If the connection drops, or no bytes arrive for **60 seconds**, the phone discards everything and keeps its data unchanged.
6. After importing, the phone sends a **result** listing the files it imported successfully, then closes the stream:

   ```json
   {"type":"result","imported":["relations_delta_20260901.zip","relations_delta_20260915.zip"]}
   ```

   The desktop may now delete the listed increments, as the cloud providers do. If the phone fails, it sends an error instead:

   ```json
   {"type":"error","message":"The computer sent unexpected data."}
   ```

## Confirmation code

Noise authenticates the peer ID of each side. Both devices derive a 6-digit code from the two peer IDs, and the user compares the codes. A device relaying the connection would have its own peer ID, so the codes would differ.

1. Take the raw bytes of both peer IDs (`PeerId.bytes` in jvm-libp2p).
2. Order the two byte arrays lexicographically as unsigned bytes, and concatenate them, smaller first.
3. Compute the SHA-256 hash of the result.
4. Read the first 4 bytes of the hash as an unsigned big-endian integer.
5. Take that value modulo 1,000,000, and pad it with leading zeros to 6 digits.

Worked example:

| | Peer ID | Raw bytes (hex) |
|-|---------|-----------------|
| A | `12D3KooWAxzCBFVtEyKLnRm43gHxigCoe2d4UmkRTXhBNSv5bcCx` | `0024080112201111111111111111111111111111111111111111111111111111111111111111` |
| B | `12D3KooWRu4WooR8vsxQcS3DShP228mPb87YKpUdwhzCFSpUiEVB` | `002408011220eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee` |

The first 4 hash bytes are `366aaf24` = 912961316, so the code is **961316**.

## Invalid messages

The phone aborts, sends an error, and closes the stream in each of these cases:

- a message is not valid JSON, its `type` is unknown, or it arrives out of order
- the hello's `protocol` is not `1`
- a frame is larger than 65,536 bytes
- a full manifest does not contain exactly `relations_all.zip`
- an incremental manifest names a file without the `relations_delta_` prefix
- two manifest entries have the same name, or an entry has no size or a negative size
- more bytes arrive than the manifest's sizes add up to

These examples are all rejected:

```json invalid
{"type":"hello","protocol":2,"name":"Office PC"}
```

```json invalid
{"type":"manifest","files":[{"name":"relations_all.zip","size":1},{"name":"relations_all.zip","size":2}]}
```

```json invalid
{"type":"manifest","files":[{"name":"relations_delta_1.zip","size":9},{"name":"relations_delta_1.zip","size":9}]}
```

```json invalid
{"type":"manifest","files":[{"name":"relations_all.zip","size":-1}]}
```

```json invalid
{"type":"hello-there"}
```

`PeerProtocolTest` checks every JSON example in this document: the valid ones against the phone's encoder and decoder, and the `json invalid` ones for rejection. It also reproduces the worked code example.
