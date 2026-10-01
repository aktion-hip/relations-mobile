package org.elbe.relations.mobile.p2p

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records the operations and lets the test emit the events of the (simulated) desktop application.
 */
class FakePeerTransport(override val localPeerId: ByteArray = byteArrayOf(0, 1, 2, 3)) : PeerTransport {
    @Volatile
    private var listener: ((PeerEvent) -> Unit)? = null

    /** The operations in the order they were called, e.g. "discover", "connect:/ip4/...", "close:pc". */
    val calls = CopyOnWriteArrayList<String>()
    /** The frames sent (without the length header), as text. */
    val sent = CopyOnWriteArrayList<String>()

    var discoveryFailure: Exception? = null
    /** The addresses whose connect() fails. */
    val connectFailures = mutableSetOf<String>()
    /** The connection ID and the remote peer ID a successful connect() reports. */
    var connectionId = "pc"
    var remotePeerId: ByteArray = byteArrayOf(9, 9)

    override fun setListener(listener: ((PeerEvent) -> Unit)?) {
        this.listener = listener
    }

    override fun startDiscovery() {
        calls.add("discover")
        discoveryFailure?.let { throw it }
    }

    override fun stopDiscovery() {
        calls.add("stopDiscovery")
    }

    override fun connect(address: String): String {
        calls.add("connect:$address")
        if (address in connectFailures) {
            throw java.io.IOException("Connection refused: $address")
        }
        emit(PeerEvent.Connected(connectionId, remotePeerId))
        return connectionId
    }

    override fun send(connectionId: String, bytes: ByteArray) {
        calls.add("send:$connectionId")
        sent.add(String(bytes, 4, bytes.size - 4, Charsets.UTF_8))
    }

    override fun close(connectionId: String) {
        calls.add("close:$connectionId")
    }

    override fun stop() {
        calls.add("stop")
    }

    fun emit(event: PeerEvent) {
        listener?.invoke(event)
    }

    /** A computer announces itself. */
    fun found(peerId: String, vararg addresses: String) = emit(PeerEvent.Found(peerId, addresses.toList()))

    /** Sends a control frame from the desktop. */
    fun frame(connectionId: String, json: String) =
            emit(PeerEvent.Data(connectionId, PeerProtocol.frame(json.toByteArray())))

    fun data(connectionId: String, bytes: ByteArray) = emit(PeerEvent.Data(connectionId, bytes))

    fun hasListener(): Boolean = listener != null
}
