package org.elbe.relations.mobile.p2p

import org.elbe.relations.mobile.p2p.PeerProtocol.ManifestFile
import org.elbe.relations.mobile.p2p.PeerProtocol.ProtocolException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

class StreamDecoderTest {
    private val frames = mutableListOf<String>()
    private val completed = mutableListOf<String>()
    private val outputs = linkedMapOf<String, ByteArrayOutputStream>()
    private var manifest: List<ManifestFile> = emptyList()

    /** Expects the files of `manifest` once a frame with the JSON "manifest" arrives. */
    private fun newDecoder(): StreamDecoder {
        lateinit var decoder: StreamDecoder
        decoder = StreamDecoder(onFrame = { json ->
            val text = String(json, Charsets.UTF_8)
            frames.add(text)
            if (text == "manifest") {
                decoder.expectFiles(manifest) { file -> ByteArrayOutputStream().also { outputs[file.name] = it } }
            }
        }, onFileComplete = { completed.add(it.name) })
        return decoder
    }

    private var decoder = newDecoder()

    private fun frame(text: String) = PeerProtocol.frame(text.toByteArray())

    /** Feeds the bytes in chunks of the given size. */
    private fun feed(bytes: ByteArray, chunk: Int) {
        bytes.toList().chunked(chunk).forEach { decoder.feed(it.toByteArray()) }
    }

    private fun stream(): ByteArray {
        manifest = listOf(ManifestFile("a", 3), ManifestFile("b", 0), ManifestFile("c", 5))
        return frame("hello") + frame("manifest") + "AAA".toByteArray() + "CCCCC".toByteArray()
    }

    private fun assertStream() {
        assertEquals(listOf("hello", "manifest"), frames)
        assertEquals(listOf("a", "b", "c"), completed)
        assertArrayEquals("AAA".toByteArray(), outputs.getValue("a").toByteArray())
        assertEquals(0, outputs.getValue("b").size())
        assertArrayEquals("CCCCC".toByteArray(), outputs.getValue("c").toByteArray())
        assertTrue(decoder.isComplete)
    }

    @Test
    fun testSingleChunk() {
        feed(stream(), Int.MAX_VALUE)
        assertStream()
    }

    @Test
    fun testOneByteChunks() {
        feed(stream(), 1)
        assertStream()
    }

    @Test
    fun testEveryChunkSize() {
        // splits inside the length prefix, inside a frame and across file boundaries
        val bytes = stream()
        (2..bytes.size).forEach { size ->
            frames.clear(); completed.clear(); outputs.clear()
            decoder = newDecoder()
            feed(bytes, size)
            assertEquals("chunk size $size", listOf("a", "b", "c"), completed)
            assertArrayEquals("chunk size $size", "CCCCC".toByteArray(), outputs.getValue("c").toByteArray())
        }
    }

    @Test
    fun testFramesWithoutFiles() {
        feed(frame("hello") + frame("") + frame("third"), 2)
        assertEquals(listOf("hello", "", "third"), frames)
        assertFalse(decoder.isComplete)
    }

    @Test
    fun testOnlyEmptyFile() {
        manifest = listOf(ManifestFile("empty", 0))
        feed(frame("manifest"), 3)
        assertEquals(listOf("empty"), completed)
        assertTrue(decoder.isComplete)
    }

    @Test
    fun testOversizedFrame() {
        val small = StreamDecoder(maxFrame = 10, onFrame = { }, onFileComplete = { })
        small.feed(PeerProtocol.frame(ByteArray(10)))
        try {
            small.feed(byteArrayOf(0, 0, 0, 11))
            fail("oversized frame accepted")
        } catch (e: ProtocolException) {
            // expected
        }
    }

    @Test
    fun testMaximalFrameLengthHeader() {
        // 0xFFFFFFFF must not be read as a negative length
        try {
            decoder.feed(byteArrayOf(-1, -1, -1, -1))
            fail("oversized frame accepted")
        } catch (e: ProtocolException) {
            // expected
        }
    }

    @Test
    fun testMoreDataThanAnnounced() {
        feed(stream(), 4)
        try {
            decoder.feed(byteArrayOf(1))
            fail("extra byte accepted")
        } catch (e: ProtocolException) {
            // expected
        }
    }

    @Test
    fun testExtraBytesInSameChunk() {
        try {
            feed(stream() + byteArrayOf(9), Int.MAX_VALUE)
            fail("extra byte accepted")
        } catch (e: ProtocolException) {
            assertEquals(listOf("a", "b", "c"), completed)
        }
    }
}
