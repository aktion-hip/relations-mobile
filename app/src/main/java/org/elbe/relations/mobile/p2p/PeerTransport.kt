package org.elbe.relations.mobile.p2p

/**
 * The events a PeerTransport reports, see design.md (Decision 6).
 */
sealed class PeerEvent {
    /**
     * The stream for PeerProtocol.PROTOCOL_ID to the computer is open.
     *
     * @param remotePeerId ByteArray the raw bytes of the computer's (Noise-authenticated) peer ID
     */
    class Connected(val connectionId: String, val remotePeerId: ByteArray) : PeerEvent()

    /** Bytes received on the stream, arbitrary chunk boundaries. */
    class Data(val connectionId: String, val bytes: ByteArray) : PeerEvent()

    /** The stream or the connection has been closed (by either side) or failed. */
    data class Closed(val connectionId: String) : PeerEvent()

    /** The transport failed and cannot be used anymore. */
    data class Failed(val message: String?) : PeerEvent()
}

/**
 * The libp2p operations the peer-to-peer synchronization uses (dialing role).
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
     * Dials the computer and opens the stream for PeerProtocol.PROTOCOL_ID. Blocking, gives up after 10 seconds.
     * Emits PeerEvent.Connected before it returns.
     *
     * @param address String the computer's libp2p multiaddress, e.g. /ip4/192.168.1.10/tcp/47112/p2p/12D3KooW...
     * @return String the connection ID
     * @throws Exception if the connection fails or the computer's peer ID is not the one in the address
     */
    fun connect(address: String): String

    /** Writes the bytes to the stream, in the order of the calls. */
    fun send(connectionId: String, bytes: ByteArray)

    /** Closes the stream after the pending writes and its connection. */
    fun close(connectionId: String)

    /** Closes everything, the transport cannot be restarted. */
    fun stop()
}
