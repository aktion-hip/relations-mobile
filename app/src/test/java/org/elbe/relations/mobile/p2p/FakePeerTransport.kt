package org.elbe.relations.mobile.p2p

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records the operations and lets the test emit the events of the (simulated) desktop application.
 */
class FakePeerTransport(override val localPeerId: ByteArray = byteArrayOf(0, 1, 2, 3)) : PeerTransport {
    @Volatile
    private var listener: ((PeerEvent) -> Unit)? = null

    /** The operations in the order they were called, e.g. "start:47112", "close:pc". */
    val calls = CopyOnWriteArrayList<String>()
    /** The frames sent (without the length header), as text. */
    val sent = CopyOnWriteArrayList<String>()

    var address = "/ip4/192.168.1.23/tcp/47112/p2p/12D3KooWPhone"
    var startFailure: Exception? = null

    override fun setListener(listener: ((PeerEvent) -> Unit)?) {
        this.listener = listener
    }

    override fun start(port: Int): String {
        calls.add("start:$port")
        startFailure?.let { throw it }
        return address
    }

    override fun stopListening() {
        calls.add("stopListening")
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

    fun connect(connectionId: String, remotePeerId: ByteArray = byteArrayOf(9, 9)) =
            emit(PeerEvent.Connected(connectionId, remotePeerId))

    /** Sends a control frame from the desktop. */
    fun frame(connectionId: String, json: String) =
            emit(PeerEvent.Data(connectionId, PeerProtocol.frame(json.toByteArray())))

    fun data(connectionId: String, bytes: ByteArray) = emit(PeerEvent.Data(connectionId, bytes))

    fun hasListener(): Boolean = listener != null
}
