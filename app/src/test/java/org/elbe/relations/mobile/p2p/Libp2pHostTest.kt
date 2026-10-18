package org.elbe.relations.mobile.p2p

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.dsl.Builder
import io.libp2p.core.dsl.host
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.core.mux.StreamMuxerProtocol
import io.libp2p.discovery.MDnsDiscovery
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.elbe.relations.mobile.cloud.AbstractCloudProvider
import org.elbe.relations.mobile.cloud.PeerAnswer
import org.elbe.relations.mobile.cloud.PeerCloudProvider
import org.elbe.relations.mobile.cloud.PeerImport
import org.elbe.relations.mobile.cloud.SyncSession
import org.elbe.relations.mobile.cloud.SyncState
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs the phone's host and a jvm-libp2p host playing the (listening) Relations desktop application over 127.0.0.1.
 */
class Libp2pHostTest {
    private val dir: File = Files.createTempDirectory("libp2pTest").toFile()
    private val events = CopyOnWriteArrayList<PeerEvent>()
    private val phones = mutableListOf<Libp2pHost>()
    private val computers = mutableListOf<Computer>()

    @After
    fun tearDown() {
        computers.forEach { it.stop() }
        phones.forEach { it.stop() }
        dir.deleteRecursively()
    }

    private fun phone(): Libp2pHost {
        val phone = Libp2pHost(File(dir, "p2p_identity.key"), Libp2pHost.loopback())
        phones.add(phone)
        phone.setListener { events.add(it) }
        return phone
    }

    /** The desktop side: listens, optionally announces itself, and exchanges raw bytes on the stream the phone opens. */
    private class Computer(announce: Boolean) {
        val received = ByteArrayOutputStream()
        @Volatile
        var stream: Stream? = null

        private val protocol = object : ProtocolHandler<Unit>(Long.MAX_VALUE, Long.MAX_VALUE) {
            override fun onStartResponder(stream: Stream): CompletableFuture<Unit> {
                stream.pushHandler(object : ProtocolMessageHandler<ByteBuf> {
                    override fun onActivated(stream: Stream) {
                        this@Computer.stream = stream
                    }

                    override fun onMessage(stream: Stream, msg: ByteBuf) {
                        val bytes = ByteArray(msg.readableBytes())
                        msg.readBytes(bytes)
                        synchronized(received) { received.write(bytes) }
                    }
                })
                return CompletableFuture.completedFuture(Unit)
            }
        }

        private val binding = object : StrictProtocolBinding<Unit>(PeerProtocol.PROTOCOL_ID, protocol) {}

        val host: Host = host(Builder.Defaults.None) {
            identity { random(KeyType.ED25519) }
            transports { add(::TcpTransport) }
            secureChannels { add { key, muxers -> NoiseXXSecureChannel(key, muxers) } }
            muxers { add(StreamMuxerProtocol.Mplex) }
            network { listen("/ip4/127.0.0.1/tcp/0") }
            protocols { add(binding) }
        }.also { it.start().get(10, TimeUnit.SECONDS) }

        val peerId: PeerId = host.peerId

        /** e.g. /ip4/127.0.0.1/tcp/40000 */
        val listenAddress: String = host.listenAddresses().first().toString().substringBefore("/p2p/")

        /** The full multiaddress the phone dials. */
        val address: String = "$listenAddress/p2p/${peerId.toBase58()}"

        private val discovery: MDnsDiscovery? = if (announce) {
            MDnsDiscovery(host, PeerProtocol.MDNS_SERVICE_TAG, 1, Libp2pHost.loopback()).also {
                it.start().get(10, TimeUnit.SECONDS)
            }
        } else null

        fun send(bytes: ByteArray) {
            stream!!.writeAndFlush(Unpooled.wrappedBuffer(bytes))
        }

        fun receivedBytes(): ByteArray = synchronized(received) { received.toByteArray() }

        fun stop() {
            discovery?.stop()
            host.stop().get(10, TimeUnit.SECONDS)
        }
    }

    private fun computer(announce: Boolean = false): Computer = Computer(announce).also { computers.add(it) }

    private fun await(description: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timeout waiting for $description, events: $events")
            }
            Thread.sleep(10)
        }
    }

    private fun connected(): List<PeerEvent.Connected> = events.filterIsInstance<PeerEvent.Connected>()

    private fun data(connectionId: String): ByteArray = events.filterIsInstance<PeerEvent.Data>()
            .filter { it.connectionId == connectionId }
            .fold(ByteArray(0)) { all, d -> all + d.bytes }

    private fun otherPeerId(): PeerId = PeerId.fromPubKey(Libp2pHost.loadOrCreateKey(File(dir, "other.key")).publicKey())

    @Test
    fun testIdentity() {
        val phone = phone()
        assertTrue(phone.peerId, phone.peerId.startsWith("12D3KooW"))
        assertArrayEquals(PeerId.fromBase58(phone.peerId).bytes, phone.localPeerId)
    }

    @Test
    fun testIdentitySurvivesRestart() {
        val first = phone()
        first.stop()
        phones.clear()
        assertEquals(first.peerId, phone().peerId)
    }

    @Test
    fun testDiscovery() {
        val computer = computer(announce = true)
        val phone = phone()
        phone.startDiscovery()
        await("found", 20_000) { events.any { it is PeerEvent.Found && it.peerId == computer.peerId.toBase58() } }
        val found = events.filterIsInstance<PeerEvent.Found>().first { it.peerId == computer.peerId.toBase58() }
        assertTrue(found.addresses.toString(), found.addresses.contains(computer.listenAddress))
        phone.stopDiscovery()
    }

    @Test
    fun testDiscoveryWithoutAddress() {
        val phone = Libp2pHost(File(dir, "p2p_identity.key"), null)
        phones.add(phone)
        // no interface to search on: nothing happens
        phone.startDiscovery()
        phone.stopDiscovery()
    }

    @Test
    fun testParseAnswer() {
        val id = otherPeerId().toBase58()
        val txt = byteArrayOf(id.length.toByte()) + id.toByteArray()
        val v4 = InetAddress.getByName("192.168.1.10")
        val v6 = InetAddress.getByName("fe80::1")
        assertEquals(PeerEvent.Found(id, listOf("/ip4/192.168.1.10/tcp/47112")), Libp2pHost.parseAnswer(txt, 47112, listOf(v6, v4)))
        assertNull(Libp2pHost.parseAnswer(null, 47112, listOf(v4)))
        assertNull(Libp2pHost.parseAnswer(txt, null, listOf(v4)))
        assertNull(Libp2pHost.parseAnswer(txt, 47112, listOf(v6)))
        // wrong length byte, invalid id
        assertNull(Libp2pHost.parseAnswer(byteArrayOf(100) + id.toByteArray(), 47112, listOf(v4)))
        assertNull(Libp2pHost.parseAnswer(byteArrayOf(4) + "0OIl".toByteArray(), 47112, listOf(v4)))
    }

    @Test
    fun testExchangeAndConfirmationCode() {
        val phone = phone()
        val computer = computer()
        val connectionId = phone.connect(computer.address)
        val connection = connected().single()
        assertEquals(connectionId, connection.connectionId)
        // Noise-authenticated peer id of the computer
        assertArrayEquals(computer.peerId.bytes, connection.remotePeerId)
        await("computer stream") { computer.stream != null }
        // both sides compute the same code
        assertEquals(PeerProtocol.confirmationCode(computer.peerId.bytes, computer.stream!!.remotePeerId().bytes),
                PeerProtocol.confirmationCode(phone.localPeerId, connection.remotePeerId))

        // desktop -> phone: hello, manifest and file bytes, arbitrary chunking
        val hello = PeerProtocol.frame("""{"type":"hello","protocol":1,"name":"PC"}""".toByteArray())
        val manifest = PeerProtocol.frame("""{"type":"manifest","files":[{"name":"relations_all.zip","size":100000}]}""".toByteArray())
        val file = ByteArray(100_000) { (it % 251).toByte() }
        computer.send(hello)
        computer.send(manifest + file.copyOfRange(0, 1000))
        computer.send(file.copyOfRange(1000, file.size))
        val expected = hello + manifest + file
        await("all data") { data(connectionId).size == expected.size }
        assertArrayEquals(expected, data(connectionId))

        // phone -> desktop, then close
        val request = PeerProtocol.encodeRequest(false)
        phone.send(connectionId, request)
        await("request") { computer.receivedBytes().size == request.size }
        assertArrayEquals(request, computer.receivedBytes())
        phone.close(connectionId)
        await("closed") { events.contains(PeerEvent.Closed(connectionId)) }
    }

    @Test
    fun testWrongPeerId() {
        val phone = phone()
        val computer = computer()
        assertThrows(Exception::class.java) { phone.connect("${computer.listenAddress}/p2p/${otherPeerId().toBase58()}") }
        assertTrue(connected().isEmpty())
    }

    @Test
    fun testNothingListening() {
        val phone = phone()
        val free = ServerSocket(0).use { it.localPort }
        val start = System.currentTimeMillis()
        assertThrows(Exception::class.java) { phone.connect("/ip4/127.0.0.1/tcp/$free/p2p/${otherPeerId().toBase58()}") }
        assertTrue(System.currentTimeMillis() - start < 12_000)
    }

    @Test
    fun testComputerDisconnects() {
        val phone = phone()
        val computer = computer()
        val connectionId = phone.connect(computer.address)
        computer.stop()
        computers.clear()
        await("closed") { events.contains(PeerEvent.Closed(connectionId)) }
    }

    /**
     * The whole stack: PeerCloudProvider on a real Libp2pHost finds the listening computer through mDNS on 127.0.0.1,
     * connects, confirms and receives a full synchronization.
     */
    @Test
    fun testProviderEndToEnd() {
        val states = CopyOnWriteArrayList<SyncState>()
        val session = SyncSession { states.add(it) }
        val imported = CopyOnWriteArrayList<String>()
        val importer = object : PeerImport {
            override fun full(file: File, progress: (Int, Int) -> Unit) {
                imported.add(file.readText())
            }

            override fun incremental(file: File, progress: (Int, Int) -> Unit) = throw IllegalStateException()

            override fun noIncremental() = Unit
        }
        val computer = computer(announce = true)
        val phone = Libp2pHost(File(dir, "p2p_identity.key"), Libp2pHost.loopback())
        val provider = PeerCloudProvider(phone, importer, dir, { it.name },
                localAddress = LocalAddress.Local(Libp2pHost.loopback(), 8)) { _, _ -> }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<AbstractCloudProvider.SyncResult> { provider.synchronize(false, session) }
            // the computer is listed with its address
            await("listed", 20_000) {
                (states.lastOrNull() as? SyncState.SelectComputer)?.computers?.any { it.peerId == computer.peerId.toBase58() } == true
            }
            val listed = (states.last() as SyncState.SelectComputer).computers.single()
            assertEquals(computer.address, listed.preferred)
            session.answer(PeerAnswer.Connect(computer = listed.peerId))

            await("computer stream") { computer.stream != null }
            computer.send(PeerProtocol.frame("""{"type":"hello","protocol":1,"name":"Office PC"}""".toByteArray()))
            await("confirm") { states.lastOrNull() is SyncState.ConfirmPeer }
            val confirm = states.last() as SyncState.ConfirmPeer
            assertEquals("Office PC", confirm.endpointName)
            // the code the computer computes from its own view of the connection
            assertEquals(PeerProtocol.confirmationCode(computer.peerId.bytes, computer.stream!!.remotePeerId().bytes), confirm.token)

            session.answer(PeerAnswer.Accept)
            val request = PeerProtocol.encodeRequest(false)
            await("request") { computer.receivedBytes().size >= request.size }
            assertArrayEquals(request, computer.receivedBytes())

            val content = ByteArray(300_000) { 'x'.code.toByte() }
            computer.send(PeerProtocol.frame("""{"type":"manifest","files":[{"name":"relations_all.zip","size":${content.size}}]}""".toByteArray()))
            content.toList().chunked(70_000).forEach { computer.send(it.toByteArray()) }

            val result = future.get(20, TimeUnit.SECONDS)
            assertTrue(result.message, result.value)
            assertEquals(listOf(String(content)), imported)
            // the result reaches the computer although the phone closes the stream right after it
            val expected = request + PeerProtocol.encodeResult(listOf("relations_all.zip"))
            await("result") { computer.receivedBytes().size >= expected.size }
            assertArrayEquals(expected, computer.receivedBytes())
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".zip") })
        } finally {
            executor.shutdownNow()
        }
    }
}
