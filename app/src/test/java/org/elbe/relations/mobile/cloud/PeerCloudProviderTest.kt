package org.elbe.relations.mobile.cloud

import org.elbe.relations.mobile.p2p.FakePeerTransport
import org.elbe.relations.mobile.p2p.PeerEvent
import org.elbe.relations.mobile.p2p.PeerProtocol
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Runs the provider on a background thread and plays the desktop application's part with the fake transport.
 */
class PeerCloudProviderTest {
    private val phoneId = byteArrayOf(1, 2, 3)
    private val computerId = byteArrayOf(7, 8, 9)
    private val transport = FakePeerTransport(phoneId)
    private val tempDir: File = Files.createTempDirectory("p2pTest").toFile()
    private val states = CopyOnWriteArrayList<SyncState>()
    private val session = SyncSession { states.add(it) }
    private val executor = Executors.newSingleThreadExecutor()

    private class RecordingImport : PeerImport {
        val imported = CopyOnWriteArrayList<String>()
        var noIncrementalCalls = 0
        var failOn: String? = null

        override fun full(file: File, progress: (Int, Int) -> Unit) = record("full", file, progress)

        override fun incremental(file: File, progress: (Int, Int) -> Unit) = record("incr", file, progress)

        private fun record(kind: String, file: File, progress: (Int, Int) -> Unit) {
            val content = file.readText()
            if (content == failOn) {
                throw IllegalStateException("broken file")
            }
            progress(1, 1)
            imported.add("$kind:$content")
        }

        override fun noIncremental() {
            noIncrementalCalls++
        }
    }

    private val importer = RecordingImport()

    private fun start(incremental: Boolean, connectTimeoutMs: Long = 5000, stallTimeoutMs: Long = 5000): Future<AbstractCloudProvider.SyncResult> {
        val provider = PeerCloudProvider(transport, importer, tempDir, { it.name }, connectTimeoutMs, stallTimeoutMs) { _, _ -> }
        return executor.submit<AbstractCloudProvider.SyncResult> { provider.synchronize(incremental, session) }
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
        tempDir.deleteRecursively()
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timeout waiting for $description, states: $states, calls: ${transport.calls}")
            }
            Thread.sleep(5)
        }
    }

    private fun result(future: Future<AbstractCloudProvider.SyncResult>) = future.get(5, TimeUnit.SECONDS)

    private val hello = """{"type":"hello","protocol":1,"name":"PC"}"""

    private fun waiting() = await("waiting") { states.lastOrNull() is SyncState.WaitingForPeer }

    /** Connects as "pc", sends hello and waits for the confirmation. */
    private fun connectAndHello() {
        waiting()
        transport.connect("pc", computerId)
        transport.frame("pc", hello)
        await("confirm") { states.lastOrNull() is SyncState.ConfirmPeer }
    }

    /** Connects, confirms and returns once the phone has sent its request. */
    private fun connect() {
        connectAndHello()
        session.answer(PeerAnswer.ACCEPT)
        await("request") { transport.sent.size == 1 }
    }

    private fun manifest(vararg files: Pair<String, String>): String {
        val entries = files.joinToString(",") { (name, content) -> """{"name":"$name","size":${content.length}}""" }
        return """{"type":"manifest","files":[$entries]}"""
    }

    /** Sends the manifest and the files' content in one chunk. */
    private fun sendFiles(vararg files: Pair<String, String>) {
        transport.data("pc", PeerProtocol.frame(manifest(*files).toByteArray()) + files.joinToString("") { it.second }.toByteArray())
    }

    private fun lastSent(): JSONObject = JSONObject(transport.sent.last())

    private fun assertCleanedUp() {
        assertTrue("close expected: ${transport.calls}", transport.calls.contains("close:pc"))
        assertTrue(transport.calls.contains("stop"))
        assertFalse(transport.hasListener())
        assertEquals("temporary files left", 0, tempDir.listFiles()!!.size)
    }

    @Test
    fun testFullSynchronization() {
        val future = start(false)
        waiting()
        assertEquals(SyncState.WaitingForPeer(transport.address), states.last())
        assertEquals("start:47112", transport.calls.first())
        transport.connect("pc", computerId)
        transport.frame("pc", hello)
        await("confirm") { states.lastOrNull() is SyncState.ConfirmPeer }
        assertEquals(SyncState.ConfirmPeer("PC", PeerProtocol.confirmationCode(phoneId, computerId)), states.last())
        assertTrue(transport.calls.contains("stopListening"))
        // nothing is sent before the user accepts
        assertTrue(transport.sent.isEmpty())
        session.answer(PeerAnswer.ACCEPT)
        await("request") { transport.sent.size == 1 }
        val request = JSONObject(transport.sent[0])
        assertEquals("request", request.getString("type"))
        assertEquals("full", request.getString("mode"))
        sendFiles("relations_all.zip" to "ALL")

        val result = result(future)
        assertTrue(result.value)
        assertEquals("SUCCESS", result.message)
        assertEquals(listOf("full:ALL"), importer.imported)
        assertEquals("result", lastSent().getString("type"))
        assertEquals("relations_all.zip", lastSent().getJSONArray("imported").getString(0))
        assertTrue(states.contains(SyncState.Running(0, 0, true)))
        assertTrue(states.contains(SyncState.Running(1, 1, false)))
        assertCleanedUp()
    }

    @Test
    fun testIncrementalAppliedInManifestOrder() {
        val future = start(true)
        connect()
        assertEquals("incremental", JSONObject(transport.sent[0]).getString("mode"))
        // the manifest in one chunk, the files byte by byte
        val files = arrayOf("relations_delta_a.zip" to "A1", "relations_delta_b.zip" to "B22", "relations_delta_c.zip" to "C333")
        transport.frame("pc", manifest(*files))
        files.joinToString("") { it.second }.toByteArray().forEach { transport.data("pc", byteArrayOf(it)) }

        assertTrue(result(future).value)
        assertEquals(listOf("incr:A1", "incr:B22", "incr:C333"), importer.imported)
        val imported = lastSent().getJSONArray("imported")
        assertEquals(listOf("relations_delta_a.zip", "relations_delta_b.zip", "relations_delta_c.zip"),
                (0 until imported.length()).map { imported.getString(it) })
        assertCleanedUp()
    }

    @Test
    fun testHelloAndManifestSplitAcrossChunks() {
        val future = start(false)
        waiting()
        transport.connect("pc", computerId)
        val helloFrame = PeerProtocol.frame(hello.toByteArray())
        transport.data("pc", helloFrame.copyOfRange(0, 2))
        transport.data("pc", helloFrame.copyOfRange(2, helloFrame.size))
        await("confirm") { states.lastOrNull() is SyncState.ConfirmPeer }
        session.answer(PeerAnswer.ACCEPT)
        await("request") { transport.sent.size == 1 }
        val bytes = PeerProtocol.frame(manifest("relations_all.zip" to "ALL").toByteArray()) + "ALL".toByteArray()
        bytes.toList().chunked(3).forEach { transport.data("pc", it.toByteArray()) }
        assertTrue(result(future).value)
        assertEquals(listOf("full:ALL"), importer.imported)
    }

    @Test
    fun testNoIncrementalData() {
        val future = start(true)
        connect()
        transport.frame("pc", """{"type":"manifest","files":[]}""")
        val result = result(future)
        assertFalse(result.value)
        assertEquals("NO_INCREMENTAL", result.message)
        assertEquals(1, importer.noIncrementalCalls)
        assertEquals(0, lastSent().getJSONArray("imported").length())
        assertTrue(importer.imported.isEmpty())
        assertCleanedUp()
    }

    @Test
    fun testUserRejects() {
        val future = start(false)
        connectAndHello()
        session.answer(PeerAnswer.REJECT)
        assertEquals("CANCELED", result(future).message)
        assertTrue(transport.sent.isEmpty())
        assertCleanedUp()
    }

    @Test
    fun testSecondConnectionClosed() {
        val future = start(false)
        waiting()
        transport.connect("pc", computerId)
        transport.connect("other")
        await("close other") { transport.calls.contains("close:other") }
        transport.frame("pc", hello)
        await("confirm") { states.lastOrNull() is SyncState.ConfirmPeer }
        assertEquals(1, states.count { it is SyncState.ConfirmPeer })
        // also while confirming and during the transfer
        transport.connect("third")
        await("close third") { transport.calls.contains("close:third") }
        session.answer(PeerAnswer.ACCEPT)
        await("request") { transport.sent.size == 1 }
        transport.connect("fourth")
        sendFiles("relations_all.zip" to "ALL")
        assertTrue(result(future).value)
        assertTrue(transport.calls.contains("close:fourth"))
    }

    @Test
    fun testNoComputerWithinTimeout() {
        val result = result(start(false, connectTimeoutMs = 100))
        assertFalse(result.value)
        assertEquals("NO_COMPUTER", result.message)
        assertTrue(transport.calls.contains("stop"))
        assertFalse(transport.hasListener())
    }

    @Test
    fun testListenFailure() {
        transport.startFailure = java.io.IOException("no network")
        val result = result(start(false))
        assertEquals("LISTEN_FAILED", result.message)
        assertTrue(states.none { it is SyncState.WaitingForPeer })
    }

    @Test
    fun testNoHello() {
        val future = start(false, stallTimeoutMs = 200)
        waiting()
        transport.connect("pc", computerId)
        assertEquals("TIMED_OUT", result(future).message)
        assertCleanedUp()
    }

    @Test
    fun testTransferStalls() {
        val future = start(false, stallTimeoutMs = 300)
        connect()
        transport.frame("pc", manifest("relations_all.zip" to "ALL"))
        assertEquals("TIMED_OUT", result(future).message)
        assertTrue(importer.imported.isEmpty())
        assertCleanedUp()
    }

    @Test
    fun testDataKeepsTransferAlive() {
        val future = start(false, stallTimeoutMs = 300)
        connect()
        val content = "ABCD"
        transport.frame("pc", manifest("relations_all.zip" to content))
        content.forEach {
            Thread.sleep(150)
            transport.data("pc", byteArrayOf(it.code.toByte()))
        }
        assertTrue(result(future).value)
        assertEquals(listOf("full:ABCD"), importer.imported)
    }

    @Test
    fun testConnectionLostDuringTransfer() {
        val future = start(true)
        connect()
        transport.frame("pc", manifest("relations_delta_a.zip" to "AA", "relations_delta_b.zip" to "BB"))
        transport.data("pc", "AAB".toByteArray())
        await("files stored") { tempDir.listFiles()!!.size == 2 }
        transport.emit(PeerEvent.Closed("pc"))
        assertEquals("CONNECTION_LOST", result(future).message)
        assertTrue(importer.imported.isEmpty())
        assertCleanedUp()
    }

    @Test
    fun testConnectionLostWhileConfirming() {
        val future = start(false)
        connectAndHello()
        transport.emit(PeerEvent.Closed("pc"))
        assertEquals("CONNECTION_LOST", result(future).message)
    }

    @Test
    fun testCancelWhileWaiting() {
        val future = start(false)
        waiting()
        session.answer(PeerAnswer.CANCEL)
        assertEquals("CANCELED", result(future).message)
        assertTrue(transport.calls.contains("stop"))
        assertFalse(transport.hasListener())
    }

    @Test
    fun testCancelWhileReceiving() {
        val future = start(true)
        connect()
        transport.frame("pc", manifest("relations_delta_a.zip" to "AA", "relations_delta_b.zip" to "BB"))
        transport.data("pc", "AA".toByteArray())
        await("first file stored") { tempDir.listFiles()!!.isNotEmpty() }
        session.answer(PeerAnswer.CANCEL)
        assertEquals("CANCELED", result(future).message)
        assertTrue(importer.imported.isEmpty())
        assertCleanedUp()
    }

    private fun assertProtocolError(future: Future<AbstractCloudProvider.SyncResult>) {
        assertEquals("UNEXPECTED_DATA", result(future).message)
        assertEquals("error", lastSent().getString("type"))
        assertTrue(importer.imported.isEmpty())
        assertCleanedUp()
    }

    @Test
    fun testUnsupportedHelloVersion() {
        val future = start(false)
        waiting()
        transport.connect("pc", computerId)
        transport.frame("pc", """{"type":"hello","protocol":2,"name":"PC"}""")
        assertProtocolError(future)
        assertTrue(states.none { it is SyncState.ConfirmPeer })
    }

    @Test
    fun testMessageBeforeRequest() {
        val future = start(false)
        connectAndHello()
        transport.frame("pc", manifest("relations_all.zip" to "ALL"))
        assertProtocolError(future)
    }

    @Test
    fun testMalformedManifest() {
        val future = start(false)
        connect()
        transport.frame("pc", "garbage")
        assertProtocolError(future)
    }

    @Test
    fun testOversizedFrame() {
        val future = start(false)
        connect()
        transport.data("pc", byteArrayOf(0, 1, 0, 1))
        assertProtocolError(future)
    }

    @Test
    fun testMoreDataThanAnnounced() {
        val future = start(false)
        connect()
        transport.data("pc", PeerProtocol.frame(manifest("relations_all.zip" to "ALL").toByteArray()) + "ALLX".toByteArray())
        assertProtocolError(future)
    }

    @Test
    fun testDesktopError() {
        val future = start(false)
        connect()
        transport.frame("pc", """{"type":"error","message":"No export"}""")
        val result = result(future)
        assertFalse(result.value)
        assertEquals("No export", result.message)
        assertCleanedUp()
    }

    @Test
    fun testImportFailureReportsImportedFiles() {
        importer.failOn = "B"
        val future = start(true)
        connect()
        sendFiles("relations_delta_a.zip" to "A", "relations_delta_b.zip" to "B")
        assertEquals("IMPORT_FAILED", result(future).message)
        assertEquals("result", lastSent().getString("type"))
        assertEquals(1, lastSent().getJSONArray("imported").length())
        assertCleanedUp()
    }

    @Test
    fun testCancelIgnoredWhileImporting() {
        val future = start(false)
        connect()
        sendFiles("relations_all.zip" to "ALL")
        await("importing") { states.contains(SyncState.Running(0, 0, false)) }
        session.answer(PeerAnswer.CANCEL)
        assertTrue(result(future).value)
    }
}
