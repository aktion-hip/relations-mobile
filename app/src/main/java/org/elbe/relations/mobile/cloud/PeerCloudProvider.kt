package org.elbe.relations.mobile.cloud

import android.content.Context
import android.content.res.Resources
import android.util.Log
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.dbimport.DBImportFull
import org.elbe.relations.mobile.dbimport.DBImportIncremental
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.p2p.LocalAddress
import org.elbe.relations.mobile.p2p.PeerEvent
import org.elbe.relations.mobile.p2p.PeerProtocol
import org.elbe.relations.mobile.p2p.PeerProtocol.ManifestFile
import org.elbe.relations.mobile.p2p.PeerProtocol.ProtocolException
import org.elbe.relations.mobile.p2p.PeerProtocol.Reply
import org.elbe.relations.mobile.p2p.PeerTransport
import org.elbe.relations.mobile.p2p.StreamDecoder
import org.elbe.relations.mobile.search.IndexWriterFactory
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val TAG = "PeerCloudProvider"
const val P2P_SEARCH_TIMEOUT_MS = 120_000L
const val P2P_STALL_TIMEOUT_MS = 60_000L
/** The number of bytes logged of invalid data. */
private const val LOG_BYTES = 16

/**
 * The messages the peer-to-peer synchronization displays.
 */
enum class PeerMessage {
    SUCCESS, NO_INCREMENTAL, CANCELED, NO_COMPUTER, CONNECT_FAILED, CONNECTION_LOST, TIMED_OUT, UNEXPECTED_DATA, SEARCH_FAILED, IMPORT_FAILED
}

/**
 * Imports the received files into the database and the search index.
 */
interface PeerImport {
    fun full(file: File, progress: (Int, Int) -> Unit)

    fun incremental(file: File, progress: (Int, Int) -> Unit)

    /** Called if there is no incremental data, i.e. the next synchronization has to be a full one. */
    fun noIncremental()
}

/**
 * The default PeerImport using the same importers as the cloud providers.
 */
class DBPeerImport(private val context: Context, private val factory: IndexWriterFactory) : PeerImport {
    override fun full(file: File, progress: (Int, Int) -> Unit) {
        factory.setOpenMode(true)
        XMLImporter(file).import(DBImportFull(context, factory).setProgress(progress))
    }

    override fun incremental(file: File, progress: (Int, Int) -> Unit) {
        factory.setOpenMode(false)
        XMLImporter(file).import(DBImportIncremental(context, factory).setProgress(progress))
    }

    override fun noIncremental() {
        AbstractCloudProvider.switchIncrementalVal(context)
    }
}

/**
 * Receives the data from the Relations desktop application via libp2p, see docs/peer-to-peer-protocol.md.
 *
 * @param transport PeerTransport the libp2p operations
 * @param importer PeerImport imports the received files
 * @param tempDir File the directory for the received files
 * @param text (PeerMessage) -> String the message to display
 * @param searchTimeoutMs Long how long to wait for the user to select a computer
 * @param stallTimeoutMs Long how long to wait for data from the computer
 * @param editable Boolean true if the user can enter a connection string (debug builds)
 * @param localAddress LocalAddress.Local? this device's address, to try the computer's addresses in its subnet first
 * @param log (String, Exception?) -> Unit logs a failed synchronization
 */
class PeerCloudProvider(private val transport: PeerTransport,
                        private val importer: PeerImport,
                        private val tempDir: File,
                        private val text: (PeerMessage) -> String,
                        private val searchTimeoutMs: Long = P2P_SEARCH_TIMEOUT_MS,
                        private val stallTimeoutMs: Long = P2P_STALL_TIMEOUT_MS,
                        private val editable: Boolean = false,
                        private val localAddress: LocalAddress.Local? = null,
                        private val log: (String, Exception?) -> Unit = { message, e -> Log.w(TAG, message, e) }) : InteractiveCloudProvider {

    private sealed class Event {
        class Transport(val event: PeerEvent) : Event()
        class User(val answer: PeerAnswer) : Event()
    }

    /** Ends the synchronization with the given result. */
    private class Abort(val result: AbstractCloudProvider.SyncResult) : Exception()

    /** What the next control frame must be. */
    private enum class Phase { HELLO, CONFIRM, MANIFEST, FILES }

    private val mEvents = LinkedBlockingQueue<Event>()
    private val mReceived = LinkedHashMap<String, File>()
    private var mConnection: String? = null
    private var mPhase = Phase.HELLO
    private var mIncremental = false
    private var mHello: PeerProtocol.Hello? = null
    private var mReply: Reply? = null
    private val mDecoder = StreamDecoder(onFrame = { onFrame(it) }, onFileComplete = { })

    override fun synchronize(incremental: Boolean, session: SyncSession): AbstractCloudProvider.SyncResult {
        mIncremental = incremental
        transport.setListener { mEvents.add(Event.Transport(it)) }
        session.setAnswerListener { mEvents.add(Event.User(it)) }
        try {
            try {
                transport.startDiscovery()
            } catch (e: Exception) {
                log("Could not start searching.", e)
                // a connection string can be entered anyway
                if (!editable) {
                    return fail(text(PeerMessage.SEARCH_FAILED))
                }
            }
            val targets = awaitSelection(session)
            transport.stopDiscovery()
            val (connection, remotePeerId) = connect(targets, session)
            val hello = awaitHello(connection)
            session.confirmPeer(hello.name.ifBlank { "Relations" },
                    PeerProtocol.confirmationCode(transport.localPeerId, remotePeerId))
            awaitConfirmation(connection)
            session.receiving()
            mPhase = Phase.MANIFEST
            transport.send(connection, PeerProtocol.encodeRequest(incremental))
            val files = when (val reply = awaitReply(connection)) {
                is Reply.Error -> return fail(reply.message.ifEmpty { text(PeerMessage.UNEXPECTED_DATA) })
                is Reply.Manifest -> reply.files
            }
            if (files.isEmpty()) {
                transport.send(connection, PeerProtocol.encodeResult(emptyList()))
                importer.noIncremental()
                return fail(text(PeerMessage.NO_INCREMENTAL))
            }
            awaitFiles(connection)
            session.importing()
            return import(connection, files, session)
        } catch (e: Abort) {
            return e.result
        } finally {
            cleanup()
        }
    }

    // --- phases

    /**
     * Lists the found computers until the user selects one.
     *
     * @return List<String> the multiaddresses to try, in this order
     */
    private fun awaitSelection(session: SyncSession): List<String> {
        val computers = LinkedHashMap<String, FoundComputer>()
        session.selectComputer(emptyList(), editable)
        val deadline = now() + searchTimeoutMs
        while (true) {
            when (val event = next(deadline) ?: throw Abort(fail(text(PeerMessage.NO_COMPUTER)))) {
                is Event.User -> when (val answer = event.answer) {
                    PeerAnswer.Cancel -> throw Abort(fail(text(PeerMessage.CANCELED)))
                    is PeerAnswer.Connect -> {
                        val targets = when {
                            editable && answer.address != null -> listOf(answer.address)
                            answer.computer != null -> computers[answer.computer]?.let { computer ->
                                computer.addresses.map { "$it/p2p/${computer.peerId}" }
                            }
                            else -> null
                        }
                        if (!targets.isNullOrEmpty()) {
                            return targets
                        }
                    }
                    else -> Unit
                }
                is Event.Transport -> when (val e = event.event) {
                    is PeerEvent.Found -> {
                        val known = computers[e.peerId]?.addresses.orEmpty()
                        val ranked = LocalAddress.rank(known + e.addresses, localAddress)
                        // mDNS answers are repeated: publish changes only
                        if (ranked.isNotEmpty() && ranked != known) {
                            computers[e.peerId] = FoundComputer(e.peerId, ranked)
                            session.selectComputer(computers.values.toList(), editable)
                        }
                    }
                    is PeerEvent.Failed -> if (!editable) throw Abort(fail(text(PeerMessage.SEARCH_FAILED)))
                    else -> Unit
                }
            }
        }
    }

    /**
     * Tries the addresses one after the other.
     *
     * @return Pair<String, ByteArray> the connection ID and the computer's peer ID
     */
    private fun connect(targets: List<String>, session: SyncSession): Pair<String, ByteArray> {
        session.connecting()
        for (target in targets) {
            if (session.isCanceled) {
                throw Abort(fail(text(PeerMessage.CANCELED)))
            }
            val connection = try {
                transport.connect(target)
            } catch (e: Exception) {
                log("Could not connect to $target.", e)
                continue
            }
            mConnection = connection
            return connection to awaitConnected(connection)
        }
        throw Abort(fail(text(if (session.isCanceled) PeerMessage.CANCELED else PeerMessage.CONNECT_FAILED)))
    }

    /** The transport reports the connection before connect() returns. */
    private fun awaitConnected(connection: String): ByteArray {
        val deadline = now() + stallTimeoutMs
        while (true) {
            when (val event = next(deadline) ?: throw Abort(fail(text(PeerMessage.TIMED_OUT)))) {
                is Event.User -> if (event.answer == PeerAnswer.Cancel) throw Abort(fail(text(PeerMessage.CANCELED)))
                is Event.Transport -> when (val e = event.event) {
                    is PeerEvent.Connected -> if (e.connectionId == connection) return e.remotePeerId else transport.close(e.connectionId)
                    is PeerEvent.Closed -> if (e.connectionId == connection) throw Abort(fail(text(PeerMessage.CONNECTION_LOST)))
                    is PeerEvent.Failed -> throw Abort(fail(text(PeerMessage.CONNECTION_LOST)))
                    else -> Unit
                }
            }
        }
    }

    private fun awaitHello(connection: String): PeerProtocol.Hello {
        val deadline = now() + stallTimeoutMs
        while (mHello == null) {
            nextData(connection, deadline)
        }
        return mHello!!
    }

    private fun awaitConfirmation(connection: String) {
        while (true) {
            when (val event = next(Long.MAX_VALUE)) {
                is Event.User -> when (event.answer) {
                    PeerAnswer.Accept -> return
                    PeerAnswer.Reject, PeerAnswer.Cancel -> throw Abort(fail(text(PeerMessage.CANCELED)))
                    is PeerAnswer.Connect -> Unit
                }
                is Event.Transport -> handleTransport(connection, event.event)
                null -> Unit
            }
        }
    }

    private fun awaitReply(connection: String): Reply {
        val deadline = now() + stallTimeoutMs
        while (mReply == null) {
            nextData(connection, deadline)
        }
        return mReply!!
    }

    private fun awaitFiles(connection: String) {
        while (!mDecoder.isComplete) {
            nextData(connection, now() + stallTimeoutMs)
        }
    }

    /** Called by the decoder (on this thread) for each complete control frame. */
    private fun onFrame(json: ByteArray) {
        when (mPhase) {
            Phase.HELLO -> {
                mHello = PeerProtocol.decodeHello(json)
                mPhase = Phase.CONFIRM
            }
            Phase.MANIFEST -> {
                val reply = PeerProtocol.decodeReply(json, mIncremental)
                mReply = reply
                if (reply is Reply.Manifest && reply.files.isNotEmpty()) {
                    mPhase = Phase.FILES
                    // the file bytes may follow in the same chunk
                    mDecoder.expectFiles(reply.files) { file -> tempFile(file).outputStream() }
                }
            }
            Phase.CONFIRM, Phase.FILES -> throw ProtocolException("Unexpected message.")
        }
    }

    private fun tempFile(file: ManifestFile): File =
            File.createTempFile("relationsP2p", ".zip", tempDir).also { mReceived[file.name] = it }

    private fun import(connection: String, files: List<ManifestFile>, session: SyncSession): AbstractCloudProvider.SyncResult {
        val imported = mutableListOf<String>()
        try {
            files.forEach { file ->
                val local = mReceived.getValue(file.name)
                val progress: (Int, Int) -> Unit = { current, max -> session.progress(current, max) }
                if (mIncremental) importer.incremental(local, progress) else importer.full(local, progress)
                imported.add(file.name)
            }
        } catch (e: Exception) {
            log("Import of the received data failed.", e)
            transport.send(connection, if (imported.isEmpty()) PeerProtocol.encodeError(text(PeerMessage.IMPORT_FAILED))
                    else PeerProtocol.encodeResult(imported))
            return fail(text(PeerMessage.IMPORT_FAILED))
        }
        transport.send(connection, PeerProtocol.encodeResult(imported))
        return AbstractCloudProvider.SyncResult(true, text(PeerMessage.SUCCESS))
    }

    // --- helpers

    private fun now(): Long = System.nanoTime() / 1_000_000

    /**
     * @return Event? the next event, null if the deadline passed
     */
    private fun next(deadline: Long): Event? {
        if (deadline == Long.MAX_VALUE) {
            return mEvents.take()
        }
        val wait = deadline - now()
        if (wait <= 0) {
            return null
        }
        return mEvents.poll(wait, TimeUnit.MILLISECONDS)
    }

    /**
     * Waits for the next data of the connection and feeds it to the decoder, handles cancel, disconnect and timeout.
     */
    private fun nextData(connection: String, deadline: Long) {
        while (true) {
            when (val event = next(deadline) ?: throw Abort(fail(text(PeerMessage.TIMED_OUT)))) {
                is Event.User -> if (event.answer == PeerAnswer.Cancel) throw Abort(fail(text(PeerMessage.CANCELED)))
                is Event.Transport -> if (handleTransport(connection, event.event)) return
            }
        }
    }

    /**
     * @return Boolean true if data of the connection has been processed
     */
    private fun handleTransport(connection: String, e: PeerEvent): Boolean {
        when (e) {
            is PeerEvent.Data -> if (e.connectionId == connection) {
                try {
                    mDecoder.feed(e.bytes)
                } catch (ex: ProtocolException) {
                    log("Invalid data in $mPhase: ${ex.message} (chunk of ${e.bytes.size} bytes, first bytes: ${firstBytes(e.bytes)})", null)
                    throw Abort(protocolError(connection))
                }
                return true
            }
            // only one computer per synchronization
            is PeerEvent.Connected -> transport.close(e.connectionId)
            is PeerEvent.Closed -> if (e.connectionId == connection) throw Abort(fail(text(PeerMessage.CONNECTION_LOST)))
            is PeerEvent.Failed -> throw Abort(fail(text(PeerMessage.CONNECTION_LOST)))
            // late mDNS answers
            is PeerEvent.Found -> Unit
        }
        return false
    }

    private fun protocolError(connection: String): AbstractCloudProvider.SyncResult {
        val message = text(PeerMessage.UNEXPECTED_DATA)
        transport.send(connection, PeerProtocol.encodeError(message))
        return fail(message)
    }

    private fun fail(message: String) = AbstractCloudProvider.SyncResult(false, message)

    /** @return String e.g. "52 4c 45 58 …", to see what the desktop sent instead of a frame */
    private fun firstBytes(bytes: ByteArray): String =
            bytes.take(LOG_BYTES).joinToString(" ") { "%02x".format(it.toInt() and 0xff) } + if (bytes.size > LOG_BYTES) " …" else ""

    private fun cleanup() {
        transport.setListener(null)
        mConnection?.let { transport.close(it) }
        transport.stop()
        mDecoder.close()
        mEvents.clear()
        mReceived.values.forEach { it.delete() }
        mReceived.clear()
    }

    companion object {
        /**
         * Creates the provider for the peer-to-peer synchronization with the app's default collaborators.
         */
        fun create(context: Context, r: Resources, factory: IndexWriterFactory, transport: PeerTransport,
                   editable: Boolean, localAddress: LocalAddress.Local?): PeerCloudProvider {
            val appContext = context.applicationContext
            return PeerCloudProvider(transport, DBPeerImport(appContext, factory), appContext.cacheDir, editable = editable, localAddress = localAddress, text = { message ->
                r.getString(when (message) {
                    PeerMessage.SUCCESS -> R.string.cloud_provider_dft_success
                    PeerMessage.NO_INCREMENTAL -> R.string.abstract_cloud_provider_no_incremental
                    PeerMessage.CANCELED -> R.string.p2p_canceled
                    PeerMessage.NO_COMPUTER -> R.string.p2p_no_computer
                    PeerMessage.CONNECT_FAILED -> R.string.p2p_connect_failed
                    PeerMessage.CONNECTION_LOST -> R.string.p2p_connection_lost
                    PeerMessage.TIMED_OUT -> R.string.p2p_timed_out
                    PeerMessage.UNEXPECTED_DATA -> R.string.p2p_unexpected_data
                    PeerMessage.SEARCH_FAILED -> R.string.p2p_search_failed
                    PeerMessage.IMPORT_FAILED -> R.string.abstract_cloud_provider_dft_error
                })
            })
        }
    }
}
