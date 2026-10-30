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
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val START_TIMEOUT_S = 10L
private const val CONNECT_TIMEOUT_S = 10L
private const val CLOSE_TIMEOUT_S = 5L

/**
 * A jvm-libp2p host (TCP, Noise, mplex) that dials the Relations desktop application and opens the stream for
 * PeerProtocol.PROTOCOL_ID, see design.md of the change reverse-p2p-roles (Decisions 1 and 3).
 *
 * The host doesn't listen. The user scans or enters the desktop's address.
 *
 * Pure JVM, i.e. usable in unit tests.
 *
 * @param identityFile File the Ed25519 key, created if it doesn't exist
 */
class Libp2pHost(identityFile: File) : PeerTransport {
    private val mKey: PrivKey = loadOrCreateKey(identityFile)
    private val mStreams = ConcurrentHashMap<String, Stream>()
    private val mCounter = AtomicInteger()
    private val mBinding = SyncBinding(SyncProtocol())
    private var mHost: Host? = null

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
    private fun host(): Host = mHost ?: host(Builder.Defaults.None) {
        identity { factory = { mKey } }
        transports { add(::TcpTransport) }
        secureChannels { add { key, muxers -> NoiseXXSecureChannel(key, muxers) } }
        muxers { add(StreamMuxerProtocol.Mplex) }
        protocols { add(mBinding) }
    }.also {
        try {
            it.start().get(START_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            it.stop()
            throw e
        }
        mHost = it
    }

    override fun connect(address: String): String {
        val promise = mBinding.dial(host(), Multiaddr(address))
        try {
            return promise.controller.get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // a connection completing after the timeout is not used
            promise.stream.thenAccept { it.connection.close() }
            throw e
        }
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
        mStreams.keys.toList().forEach { close(it) }
        mHost?.stop()?.get(START_TIMEOUT_S, TimeUnit.SECONDS)
        mHost = null
    }

    // --- the /relations/sync/1.0.0 protocol, initiator side only

    private inner class SyncBinding(protocol: SyncProtocol) : StrictProtocolBinding<String>(PeerProtocol.PROTOCOL_ID, protocol)

    /** The controller is the connection ID, available once the stream is active. */
    private inner class SyncProtocol : ProtocolHandler<String>(Long.MAX_VALUE, Long.MAX_VALUE) {
        override fun onStartInitiator(stream: Stream): CompletableFuture<String> {
            val connectionId = "c${mCounter.incrementAndGet()}"
            val active = CompletableFuture<String>()
            stream.pushHandler(StreamHandler(connectionId, active))
            return active
        }

        override fun onStartResponder(stream: Stream): CompletableFuture<String> {
            stream.close()
            // CompletableFuture.failedFuture() requires API 31
            return CompletableFuture<String>().apply { completeExceptionally(IllegalStateException("This host does not listen.")) }
        }
    }

    private inner class StreamHandler(private val connectionId: String,
                                      private val active: CompletableFuture<String>) : ProtocolMessageHandler<ByteBuf> {
        override fun onActivated(stream: Stream) {
            mStreams[connectionId] = stream
            emit(PeerEvent.Connected(connectionId, stream.remotePeerId().bytes))
            active.complete(connectionId)
        }

        override fun onMessage(stream: Stream, msg: ByteBuf) {
            val bytes = ByteArray(msg.readableBytes())
            msg.readBytes(bytes)
            emit(PeerEvent.Data(connectionId, bytes))
        }

        override fun onClosed(stream: Stream) {
            active.completeExceptionally(IllegalStateException("The stream was closed."))
            mStreams.remove(connectionId)
            emit(PeerEvent.Closed(connectionId))
        }

        override fun onException(cause: Throwable?) {
            active.completeExceptionally(cause ?: IllegalStateException("The stream failed."))
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
    }
}
