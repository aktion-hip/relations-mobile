package org.elbe.relations.mobile.p2p

import org.elbe.relations.mobile.p2p.PeerProtocol.Hello
import org.elbe.relations.mobile.p2p.PeerProtocol.ManifestFile
import org.elbe.relations.mobile.p2p.PeerProtocol.ProtocolException
import org.elbe.relations.mobile.p2p.PeerProtocol.Reply
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The unit tests run in the app module directory.
 */
class PeerProtocolTest {

    private fun reply(json: String, incremental: Boolean): Reply = PeerProtocol.decodeReply(json.toByteArray(), incremental)

    private fun assertRejected(json: String, incremental: Boolean) {
        try {
            reply(json, incremental)
            fail("Expected rejection of $json")
        } catch (e: ProtocolException) {
            // expected
        }
    }

    /** @return String the JSON of a frame, checking the length header */
    private fun unframe(frame: ByteArray): String {
        val length = ByteBuffer.wrap(frame, 0, 4).int
        assertEquals(frame.size - 4, length)
        return String(frame, 4, length, Charsets.UTF_8)
    }

    @Test
    fun testFrame() {
        val frame = PeerProtocol.frame(byteArrayOf(1, 2, 3))
        assertEquals(listOf<Byte>(0, 0, 0, 3, 1, 2, 3), frame.toList())
    }

    @Test
    fun testEncodeRequest() {
        val full = JSONObject(unframe(PeerProtocol.encodeRequest(false)))
        assertEquals("request", full.getString("type"))
        assertEquals(1, full.getInt("protocol"))
        assertEquals("full", full.getString("mode"))
        assertEquals("incremental", JSONObject(unframe(PeerProtocol.encodeRequest(true))).getString("mode"))
    }

    @Test
    fun testEncodeResultAndError() {
        val result = JSONObject(unframe(PeerProtocol.encodeResult(listOf("a.zip", "b.zip"))))
        assertEquals("result", result.getString("type"))
        assertEquals("b.zip", result.getJSONArray("imported").getString(1))
        val error = JSONObject(unframe(PeerProtocol.encodeError("oops")))
        assertEquals("error", error.getString("type"))
        assertEquals("oops", error.getString("message"))
    }

    @Test
    fun testHello() {
        assertEquals(Hello("PC"), PeerProtocol.decodeHello("""{"type":"hello","protocol":1,"name":"PC"}""".toByteArray()))
        listOf("""{"type":"hello","protocol":2,"name":"PC"}""", """{"type":"hello","name":"PC"}""",
                """{"type":"manifest","files":[]}""", "no json").forEach {
            try {
                PeerProtocol.decodeHello(it.toByteArray())
                fail("Expected rejection of $it")
            } catch (e: ProtocolException) {
                // expected
            }
        }
    }

    @Test
    fun testFullManifest() {
        val r = reply("""{"type":"manifest","files":[{"name":"relations_all.zip","size":10}]}""", false)
        assertEquals(Reply.Manifest(listOf(ManifestFile("relations_all.zip", 10))), r)
    }

    @Test
    fun testIncrementalManifestKeepsOrder() {
        val r = reply("""{"type":"manifest","files":[{"name":"relations_delta_2.zip","size":2},{"name":"relations_delta_1.zip","size":0}]}""", true)
        assertEquals(listOf("relations_delta_2.zip", "relations_delta_1.zip"), (r as Reply.Manifest).files.map { it.name })
    }

    @Test
    fun testEmptyIncrementalManifest() {
        assertEquals(Reply.Manifest(emptyList()), reply("""{"type":"manifest","files":[]}""", true))
    }

    @Test
    fun testErrorReply() {
        assertEquals(Reply.Error("no export"), reply("""{"type":"error","message":"no export"}""", false))
    }

    @Test
    fun testInvalidReplies() {
        assertRejected("not json", false)
        assertRejected("""{"type":"hello-there"}""", false)
        assertRejected("""{"type":"manifest"}""", false)
        assertRejected("""{"type":"manifest","files":[{"name":"relations_all.zip"}]}""", false)
        assertRejected("""{"type":"manifest","files":[{"name":"relations_all.zip","size":"x"}]}""", false)
        assertRejected("""{"type":"manifest","files":[{"name":"relations_all.zip","size":-1}]}""", false)
    }

    @Test
    fun testWrongFileForMode() {
        assertRejected("""{"type":"manifest","files":[]}""", false)
        assertRejected("""{"type":"manifest","files":[{"name":"relations_delta_1.zip","size":1}]}""", false)
        assertRejected("""{"type":"manifest","files":[{"name":"relations_all.zip","size":1}]}""", true)
    }

    @Test
    fun testDuplicateNames() {
        assertRejected("""{"type":"manifest","files":[{"name":"relations_delta_1.zip","size":9},{"name":"relations_delta_1.zip","size":9}]}""", true)
    }

    // --- confirmation code

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val peerA = hex("002408011220" + "11".repeat(32))
    private val peerB = hex("002408011220" + "ee".repeat(32))

    @Test
    fun testConfirmationCodeIsSymmetric() {
        assertEquals(PeerProtocol.confirmationCode(peerA, peerB), PeerProtocol.confirmationCode(peerB, peerA))
    }

    @Test
    fun testConfirmationCodeMatchesIndependentComputation() {
        // A sorts before B (0x11 < 0xee), computed without PeerProtocol
        val digest = MessageDigest.getInstance("SHA-256").digest(peerA + peerB)
        val value = ((digest[0].toLong() and 0xFF) shl 24) or ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or (digest[3].toLong() and 0xFF)
        assertEquals(String.format("%06d", value % 1_000_000), PeerProtocol.confirmationCode(peerB, peerA))
    }

    @Test
    fun testConfirmationCodeUnsignedOrderAndDigits() {
        // 0x80 is larger than 0x7f when compared unsigned (signed, it would be smaller)
        val low = byteArrayOf(0x7f)
        val high = byteArrayOf(0x80.toByte())
        val expected = MessageDigest.getInstance("SHA-256").digest(low + high)
        val value = ByteBuffer.wrap(expected, 0, 4).int.toLong() and 0xFFFF_FFFFL
        assertEquals(String.format("%06d", value % 1_000_000), PeerProtocol.confirmationCode(high, low))
        // always 6 digits, including leading zeros
        (0 until 200).forEach { i ->
            val code = PeerProtocol.confirmationCode(byteArrayOf(i.toByte()), byteArrayOf((i + 1).toByte(), 7))
            assertTrue(code, code.matches(Regex("\\d{6}")))
        }
    }

    @Test
    fun testConfirmationCodeAsciiDigitsInEveryLocale() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertTrue(PeerProtocol.confirmationCode(peerA, peerB).matches(Regex("[0-9]{6}")))
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    // --- docs/peer-to-peer-protocol.md

    private val doc = File("../docs/peer-to-peer-protocol.md").readText()

    @Test
    fun testDocumentedCodeExample() {
        val rows = Regex("\\| ([AB]) \\| `[^`]+` \\| `([0-9a-f]+)` \\|").findAll(doc).associate { it.groupValues[1] to hex(it.groupValues[2]) }
        assertEquals(setOf("A", "B"), rows.keys)
        assertTrue(rows.getValue("A").contentEquals(peerA))
        val code = Regex("the code is \\*\\*(\\d{6})\\*\\*").find(doc)!!.groupValues[1]
        assertEquals(code, PeerProtocol.confirmationCode(rows.getValue("A"), rows.getValue("B")))
    }

    /**
     * Every JSON example in docs/peer-to-peer-protocol.md must be handled as documented.
     */
    @Test
    fun testDocumentedExamples() {
        val blocks = Regex("```(json[^\\n]*)\\n(.*?)\\n\\s*```", RegexOption.DOT_MATCHES_ALL).findAll(doc).toList()
        assertTrue("No examples found", blocks.size >= 13)
        blocks.forEach { block ->
            val info = block.groupValues[1].trim()
            val text = block.groupValues[2].trim()
            if (info == "json invalid") {
                assertFalse("Documented invalid example is accepted: $text", accepted(text))
                return@forEach
            }
            val json = JSONObject(text)
            when (json.getString("type")) {
                "request" -> assertSame(text, PeerProtocol.encodeRequest(json.getString("mode") == "incremental"))
                "result" -> {
                    val imported = json.getJSONArray("imported")
                    assertSame(text, PeerProtocol.encodeResult((0 until imported.length()).map { imported.getString(it) }))
                }
                "hello", "manifest", "error" -> assertTrue("Documented example is rejected: $text", accepted(text))
                else -> fail("Unknown documented message: $text")
            }
        }
    }

    private fun accepted(text: String): Boolean {
        val attempts = listOf({ PeerProtocol.decodeHello(text.toByteArray()) },
                { reply(text, true) }, { reply(text, false) })
        return attempts.any {
            try {
                it()
                true
            } catch (e: ProtocolException) {
                false
            }
        }
    }

    private fun assertSame(expected: String, actual: ByteArray) {
        assertEquals("Encoded message differs from documented one", canonical(JSONObject(expected)), canonical(JSONObject(unframe(actual))))
    }

    // android.jar's org.json API (compile classpath) has no JSONObject.similar()
    private fun canonical(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { canonical(value.get(it)) }.toSortedMap()
        is JSONArray -> (0 until value.length()).map { canonical(value.get(it)) }
        else -> value
    }
}
