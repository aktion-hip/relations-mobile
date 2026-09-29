package org.elbe.relations.mobile.p2p

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.crypto.marshalPrivateKey
import io.libp2p.core.crypto.unmarshalPrivateKey
import io.libp2p.core.dsl.Builder
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.core.mux.StreamMuxerProtocol
import io.libp2p.discovery.MDnsDiscovery
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val START_TIMEOUT_S = 10L
private const val CLOSE_TIMEOUT_S = 5L

/**
 * A jvm-libp2p host (TCP, Noise, mplex) that accepts streams for PeerProtocol.PROTOCOL_ID, see design.md (Decision 2).
 *
 * Pure JVM, i.e. usable in unit tests and by the Relations desktop application.
 *
 * @param identityFile File the Ed25519 key, created if it doesn't exist
 * @param bindAddress Inet4Address the address to listen on (the WiFi address, 127.0.0.1 in tests)
 * @param announce Boolean true to announce this host with mDNS while listening
 */
class Libp2pHost(identityFile: File,
                 private val bindAddress: Inet4Address,
                 private val announce: Boolean = true) : PeerTransport {
    private val mKey: PrivKey = loadOrCreateKey(identityFile)
    private val mStreams = ConcurrentHashMap<String, Stream>()
    private val mCounter = AtomicInteger()
    private var mHost: Host? = null
    private var mListenAddress: Multiaddr? = null
    private var mDiscovery: MDnsDiscovery? = null

    @Volatile
    private var mListener: ((PeerEvent) -> Unit)? = null

    override val localPeerId: ByteArray = PeerId.fromPubKey(mKey.publicKey()).bytes

    /** This host's peer ID in its usual (base58) representation. */
    val peerId: String = PeerId.fromPubKey(mKey.publicKey()).toBase58()

    private fun emit(event: PeerEvent) {
        mListener?.invoke(event)
    }

    override fun setListener(listener: ((PeerEvent) -> Unit)?) {
        mListener = listener
    }

    @Synchronized
    override fun start(port: Int): String {
        check(mHost == null) { "Already started." }
        var listen = listenAddress(port)
        val host = try {
            startHost(listen)
        } catch (e: Exception) {
            // the preferred port is taken
            listen = listenAddress(0)
            startHost(listen)
        }
        mHost = host
        // unlisten() expects the configured address
        mListenAddress = Multiaddr(listen)
        // e.g. /ip4/192.168.1.23/tcp/47112/p2p/12D3KooW...
        val address = host.listenAddresses().first().toString()
        if (announce) {
            mDiscovery = MDnsDiscovery(host, address = bindAddress).also {
                it.start().get(START_TIMEOUT_S, TimeUnit.SECONDS)
            }
        }
        return if (address.contains("/p2p/")) address else "$address/p2p/$peerId"
    }

    private fun listenAddress(port: Int): String = "/ip4/${bindAddress.hostAddress}/tcp/$port"

    private fun startHost(listen: String): Host {
        val host = host(Builder.Defaults.None) {
            identity { factory = { mKey } }
            transports { add(::TcpTransport) }
            secureChannels { add { key, muxers -> NoiseXXSecureChannel(key, muxers) } }
            muxers { add(StreamMuxerProtocol.Mplex) }
            network { listen(listen) }
            protocols { add(SyncBinding(SyncProtocol())) }
        }
        try {
            host.start().get(START_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            host.stop()
            throw e
        }
        return host
    }

    @Synchronized
    override fun stopListening() {
        mDiscovery?.stop()
        mDiscovery = null
        val host = mHost ?: return
        mListenAddress?.let { host.network.unlisten(it) }
        mListenAddress = null
    }

    override fun send(connectionId: String, bytes: ByteArray) {
        mStreams[connectionId]?.writeAndFlush(Unpooled.wrappedBuffer(bytes))
    }

    /** Blocking (shortly): the stream is closed after the pending writes, then the connection. */
    override fun close(connectionId: String) {
        val stream = mStreams.remove(connectionId) ?: return
        try {
            stream.close().get(CLOSE_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // closed anyway below
        }
        stream.connection.close()
    }

    @Synchronized
    override fun stop() {
        stopListening()
        mStreams.keys.toList().forEach { close(it) }
        mHost?.stop()?.get(START_TIMEOUT_S, TimeUnit.SECONDS)
        mHost = null
    }

    // --- the /relations/sync/1.0.0 protocol, responder side only

    private inner class SyncBinding(protocol: SyncProtocol) : StrictProtocolBinding<Unit>(PeerProtocol.PROTOCOL_ID, protocol)

    private inner class SyncProtocol : ProtocolHandler<Unit>(Long.MAX_VALUE, Long.MAX_VALUE) {
        override fun onStartInitiator(stream: Stream): CompletableFuture<Unit> {
            stream.close()
            // CompletableFuture.failedFuture() requires API 31
            return CompletableFuture<Unit>().apply { completeExceptionally(IllegalStateException("This host does not dial.")) }
        }

        override fun onStartResponder(stream: Stream): CompletableFuture<Unit> {
            stream.pushHandler(StreamHandler("c${mCounter.incrementAndGet()}"))
            return CompletableFuture.completedFuture(Unit)
        }
    }

    private inner class StreamHandler(private val connectionId: String) : ProtocolMessageHandler<ByteBuf> {
        override fun onActivated(stream: Stream) {
            mStreams[connectionId] = stream
            emit(PeerEvent.Connected(connectionId, stream.remotePeerId().bytes))
        }

        override fun onMessage(stream: Stream, msg: ByteBuf) {
            val bytes = ByteArray(msg.readableBytes())
            msg.readBytes(bytes)
            emit(PeerEvent.Data(connectionId, bytes))
        }

        override fun onClosed(stream: Stream) {
            mStreams.remove(connectionId)
            emit(PeerEvent.Closed(connectionId))
        }

        override fun onException(cause: Throwable?) {
            mStreams.remove(connectionId)?.close()
            emit(PeerEvent.Closed(connectionId))
        }
    }

    companion object {
        /**
         * Loads the Ed25519 key or creates (and stores) a new one.
         */
        fun loadOrCreateKey(file: File): PrivKey {
            if (file.isFile) {
                try {
                    return unmarshalPrivateKey(file.readBytes())
                } catch (e: Exception) {
                    // unreadable key: replace it
                }
            }
            val key = generateKeyPair(KeyType.ED25519).first
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(marshalPrivateKey(key))
            if (!tmp.renameTo(file)) {
                file.writeBytes(marshalPrivateKey(key))
                tmp.delete()
            }
            return key
        }

        /** @return Inet4Address the loopback address, e.g. for tests */
        fun loopback(): Inet4Address = InetAddress.getByName("127.0.0.1") as Inet4Address
    }
}
