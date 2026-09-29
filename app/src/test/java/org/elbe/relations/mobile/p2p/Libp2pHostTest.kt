package org.elbe.relations.mobile.p2p

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.Stream
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
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Runs the phone's host and a jvm-libp2p dialer playing the Relations desktop application over 127.0.0.1.
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

    private fun phone(port: Int = 0): Pair<Libp2pHost, String> {
        val phone = Libp2pHost(File(dir, "p2p_identity.key"), Libp2pHost.loopback(), announce = false)
        phones.add(phone)
        phone.setListener { events.add(it) }
        return phone to phone.start(port)
    }

    /** The desktop side: dials and exchanges raw bytes on the stream. */
    private class Computer {
        val received = ByteArrayOutputStream()
        lateinit var stream: Stream

        private val protocol = object : ProtocolHandler<Unit>(Long.MAX_VALUE, Long.MAX_VALUE) {
            override fun onStartInitiator(stream: Stream): CompletableFuture<Unit> {
                val ready = CompletableFuture<Unit>()
                stream.pushHandler(object : ProtocolMessageHandler<ByteBuf> {
                    override fun onActivated(stream: Stream) {
                        this@Computer.stream = stream
                        ready.complete(Unit)
                    }

                    override fun onMessage(stream: Stream, msg: ByteBuf) {
                        val bytes = ByteArray(msg.readableBytes())
                        msg.readBytes(bytes)
                        synchronized(received) { received.write(bytes) }
                    }
                })
                return ready
            }
        }

        private val binding = object : StrictProtocolBinding<Unit>(PeerProtocol.PROTOCOL_ID, protocol) {}

        val host: Host = host(Builder.Defaults.None) {
            identity { random() }
            transports { add(::TcpTransport) }
            secureChannels { add { key, muxers -> NoiseXXSecureChannel(key, muxers) } }
            muxers { add(StreamMuxerProtocol.Mplex) }
            protocols { add(binding) }
        }.also { it.start().get(10, TimeUnit.SECONDS) }

        val peerId: PeerId = host.peerId

        fun dial(address: String) {
            val multiaddr = Multiaddr(address)
            binding.dial(host, multiaddr).controller.get(10, TimeUnit.SECONDS)
        }

        fun send(bytes: ByteArray) {
            stream.writeAndFlush(Unpooled.wrappedBuffer(bytes))
        }

        fun receivedBytes(): ByteArray = synchronized(received) { received.toByteArray() }

        fun stop() {
            host.stop().get(10, TimeUnit.SECONDS)
        }
    }

    private fun computer(): Computer = Computer().also { computers.add(it) }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
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

    @Test
    fun testAddressAndIdentity() {
        val (phone, address) = phone()
        assertTrue(address, address.matches(Regex("/ip4/127\\.0\\.0\\.1/tcp/\\d+/p2p/12D3KooW\\w+")))
        assertTrue(address.endsWith(phone.peerId))
        assertArrayEquals(PeerId.fromBase58(phone.peerId).bytes, phone.localPeerId)
    }

    @Test
    fun testIdentitySurvivesRestart() {
        val (first, _) = phone()
        first.stop()
        phones.clear()
        val (second, _) = phone()
        assertEquals(first.peerId, second.peerId)
    }

    @Test
    fun testPreferredPortAndFallback() {
        val free = ServerSocket(0).use { it.localPort }
        val (_, address) = phone(free)
        assertTrue(address, address.contains("/tcp/$free/"))
        // the port is taken now: a second host falls back to another port
        val other = Libp2pHost(File(dir, "other.key"), Libp2pHost.loopback(), announce = false)
        phones.add(other)
        val otherAddress = other.start(free)
        assertTrue(otherAddress, !otherAddress.contains("/tcp/$free/"))
    }

    @Test
    fun testExchangeAndConfirmationCode() {
        val (phone, address) = phone()
        val computer = computer()
        computer.dial(address)
        await("connected") { connected().isNotEmpty() }
        val connection = connected().single()
        // Noise-authenticated peer id of the dialer
        assertArrayEquals(computer.peerId.bytes, connection.remotePeerId)
        // both sides compute the same code
        assertEquals(PeerProtocol.confirmationCode(computer.peerId.bytes, phone.localPeerId),
                PeerProtocol.confirmationCode(phone.localPeerId, connection.remotePeerId))

        // desktop -> phone: hello, manifest and file bytes, arbitrary chunking
        val hello = PeerProtocol.frame("""{"type":"hello","protocol":1,"name":"PC"}""".toByteArray())
        val manifest = PeerProtocol.frame("""{"type":"manifest","files":[{"name":"relations_all.zip","size":100000}]}""".toByteArray())
        val file = ByteArray(100_000) { (it % 251).toByte() }
        computer.send(hello)
        computer.send(manifest + file.copyOfRange(0, 1000))
        computer.send(file.copyOfRange(1000, file.size))
        val expected = hello + manifest + file
        await("all data") { data(connection.connectionId).size == expected.size }
        assertArrayEquals(expected, data(connection.connectionId))

        // phone -> desktop, then close
        val request = PeerProtocol.encodeRequest(false)
        phone.send(connection.connectionId, request)
        await("request") { computer.receivedBytes().size == request.size }
        assertArrayEquals(request, computer.receivedBytes())
        phone.close(connection.connectionId)
        await("closed") { events.contains(PeerEvent.Closed(connection.connectionId)) }
    }

    @Test
    fun testSecondComputerIsSeparateConnection() {
        val (_, address) = phone()
        val first = computer()
        val second = computer()
        first.dial(address)
        second.dial(address)
        await("two connections") { connected().size == 2 }
        val (a, b) = connected()
        assertNotEquals(a.connectionId, b.connectionId)
        assertEquals(setOf(first.peerId, second.peerId), connected().map { PeerId(it.remotePeerId) }.toSet())
    }

    @Test
    fun testComputerDisconnects() {
        val (_, address) = phone()
        val computer = computer()
        computer.dial(address)
        await("connected") { connected().isNotEmpty() }
        computer.stop()
        computers.clear()
        await("closed") { events.contains(PeerEvent.Closed(connected().single().connectionId)) }
    }

    @Test
    fun testStopListening() {
        val (phone, address) = phone()
        phone.stopListening()
        val computer = computer()
        val refused = try {
            computer.dial(address)
            false
        } catch (e: Exception) {
            true
        }
        assertTrue("connection accepted after stopListening()", refused)
        assertTrue(connected().isEmpty())
    }

    /**
     * The whole stack: PeerCloudProvider on a real Libp2pHost, the computer dials over 127.0.0.1.
     */
    @Test
    fun testProviderEndToEnd() {
        val states = CopyOnWriteArrayList<org.elbe.relations.mobile.cloud.SyncState>()
        val session = org.elbe.relations.mobile.cloud.SyncSession { states.add(it) }
        val imported = CopyOnWriteArrayList<String>()
        val importer = object : org.elbe.relations.mobile.cloud.PeerImport {
            override fun full(file: File, progress: (Int, Int) -> Unit) {
                imported.add(file.readText())
            }

            override fun incremental(file: File, progress: (Int, Int) -> Unit) = throw IllegalStateException()

            override fun noIncremental() = Unit
        }
        val phone = Libp2pHost(File(dir, "p2p_identity.key"), Libp2pHost.loopback(), announce = false)
        val provider = org.elbe.relations.mobile.cloud.PeerCloudProvider(phone, importer, dir, { it.name }) { _, _ -> }
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<org.elbe.relations.mobile.cloud.AbstractCloudProvider.SyncResult> {
                provider.synchronize(false, session)
            }
            await("waiting") { states.lastOrNull() is org.elbe.relations.mobile.cloud.SyncState.WaitingForPeer }
            val address = (states.last() as org.elbe.relations.mobile.cloud.SyncState.WaitingForPeer).address

            val computer = computer()
            computer.dial(address)
            computer.send(PeerProtocol.frame("""{"type":"hello","protocol":1,"name":"Office PC"}""".toByteArray()))
            await("confirm") { states.lastOrNull() is org.elbe.relations.mobile.cloud.SyncState.ConfirmPeer }
            val confirm = states.last() as org.elbe.relations.mobile.cloud.SyncState.ConfirmPeer
            assertEquals("Office PC", confirm.endpointName)
            // the code the computer computes from its own view of the connection
            assertEquals(PeerProtocol.confirmationCode(computer.peerId.bytes, computer.stream.remotePeerId().bytes), confirm.token)

            session.answer(org.elbe.relations.mobile.cloud.PeerAnswer.ACCEPT)
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
