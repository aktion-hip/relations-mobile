package org.elbe.relations.mobile.p2p

/**
 * The events a PeerTransport reports, see design.md (Decision 6).
 */
sealed class PeerEvent {
    /**
     * A computer has opened a stream for PeerProtocol.PROTOCOL_ID.
     *
     * @param remotePeerId ByteArray the raw bytes of the computer's (Noise-authenticated) peer ID
     */
    class Connected(val connectionId: String, val remotePeerId: ByteArray) : PeerEvent()

    /** Bytes received on the stream, arbitrary chunk boundaries. */
    class Data(val connectionId: String, val bytes: ByteArray) : PeerEvent()

    /** The stream or the connection has been closed (by either side) or failed. */
    data class Closed(val connectionId: String) : PeerEvent()

    /** The transport failed and cannot accept connections anymore. */
    data class Failed(val message: String?) : PeerEvent()
}

/**
 * The libp2p operations the peer-to-peer synchronization uses (listening role).
 *
 * Implementations report events to the listener on their own threads.
 */
interface PeerTransport {

    /** The raw bytes of this device's peer ID. */
    val localPeerId: ByteArray

    /**
     * @param listener ((PeerEvent) -> Unit)? the receiver of the events, null to stop receiving
     */
    fun setListener(listener: ((PeerEvent) -> Unit)?)

    /**
     * Listens for connections and announces this device on the local network (mDNS). Blocking.
     *
     * @param port Int the preferred TCP port, another one is used if it is taken
     * @return String the listen address as libp2p multiaddress, e.g. /ip4/192.168.1.23/tcp/47112/p2p/12D3KooW...
     * @throws Exception if listening is not possible
     */
    fun start(port: Int): String

    /** Stops accepting connections and announcing this device, open connections stay. */
    fun stopListening()

    /** Writes the bytes to the stream, in the order of the calls. */
    fun send(connectionId: String, bytes: ByteArray)

    /** Closes the stream after the pending writes and its connection. */
    fun close(connectionId: String)

    /** Closes everything, the transport cannot be restarted. */
    fun stop()
}
