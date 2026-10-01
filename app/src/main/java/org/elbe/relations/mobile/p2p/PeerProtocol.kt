package org.elbe.relations.mobile.p2p

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

/**
 * The messages exchanged with the Relations desktop application, see docs/peer-to-peer-protocol.md.
 *
 * Pure Kotlin (apart from org.json), i.e. independent of jvm-libp2p and Android.
 */
object PeerProtocol {
    const val PROTOCOL_ID = "/relations/sync/1.0.0"
    const val VERSION = 1
    /** The TCP port the desktop application prefers to listen on. */
    const val DEFAULT_PORT = 47112
    /** The mDNS service the desktop application announces itself with (the domain is required by jvm-libp2p). */
    const val MDNS_SERVICE_TAG = "_relations-sync._udp.local."
    /** The maximal size of a control frame's JSON. */
    const val MAX_FRAME = 65_536
    const val FILE_NAME_ALL = "relations_all.zip"
    const val FILE_PREFIX_INCR = "relations_delta_"
    const val HEADER_SIZE = 4

    private const val TYPE_HELLO = "hello"
    private const val TYPE_REQUEST = "request"
    private const val TYPE_MANIFEST = "manifest"
    private const val TYPE_RESULT = "result"
    private const val TYPE_ERROR = "error"

    /**
     * The desktop's first message.
     *
     * @param name String the computer's name to display
     */
    data class Hello(val name: String)

    /**
     * A file announced in the desktop's manifest.
     */
    data class ManifestFile(val name: String, val size: Long)

    /**
     * The desktop's reply to the request.
     */
    sealed class Reply {
        /** @param files List<ManifestFile> the files in the order they must be applied */
        data class Manifest(val files: List<ManifestFile>) : Reply()

        data class Error(val message: String) : Reply()
    }

    /**
     * The desktop sent something that violates the protocol.
     */
    class ProtocolException(message: String) : Exception(message)

    /**
     * @param incremental Boolean true to request the increments, false to request all data
     * @return ByteArray the framed request message
     */
    fun encodeRequest(incremental: Boolean): ByteArray = JSONObject()
            .put("type", TYPE_REQUEST)
            .put("protocol", VERSION)
            .put("mode", if (incremental) "incremental" else "full")
            .toFrame()

    /**
     * @param imported List<String> the names of the successfully imported files
     * @return ByteArray the framed result message
     */
    fun encodeResult(imported: List<String>): ByteArray = JSONObject()
            .put("type", TYPE_RESULT)
            .put("imported", JSONArray(imported))
            .toFrame()

    fun encodeError(message: String): ByteArray = JSONObject()
            .put("type", TYPE_ERROR)
            .put("message", message)
            .toFrame()

    /**
     * @param json ByteArray the UTF-8 JSON of a control message
     * @return ByteArray the frame, i.e. the 4-byte big-endian length followed by the JSON
     */
    fun frame(json: ByteArray): ByteArray =
            ByteBuffer.allocate(HEADER_SIZE + json.size).putInt(json.size).put(json).array()

    /**
     * @param payload ByteArray the JSON of the desktop's first frame
     * @return Hello
     * @throws ProtocolException if it is no hello or announces an unsupported protocol version
     */
    fun decodeHello(payload: ByteArray): Hello {
        val json = parse(payload)
        val type = json.optString("type")
        if (type != TYPE_HELLO) {
            throw ProtocolException("Expected hello, got '$type'.")
        }
        if (json.optInt("protocol", -1) != VERSION) {
            throw ProtocolException("Unsupported protocol version ${json.opt("protocol")}.")
        }
        return Hello(json.optString("name"))
    }

    /**
     * Decodes and validates the desktop's reply to a request.
     *
     * @param payload ByteArray the JSON of the received frame
     * @param incremental Boolean the mode of the request
     * @return Reply
     * @throws ProtocolException if the reply is malformed or doesn't match the request
     */
    fun decodeReply(payload: ByteArray, incremental: Boolean): Reply {
        val json = parse(payload)
        return when (val type = json.optString("type")) {
            TYPE_ERROR -> Reply.Error(json.optString("message"))
            TYPE_MANIFEST -> Reply.Manifest(decodeFiles(json, incremental))
            else -> throw ProtocolException("Unexpected message type '$type'.")
        }
    }

    /**
     * The 6-digit code both devices display, see the spec's "Connection confirmation".
     *
     * @param localPeerId ByteArray the raw bytes of this device's peer ID
     * @param remotePeerId ByteArray the raw bytes of the other device's peer ID
     * @return String six digits, independent of the parameters' order
     */
    fun confirmationCode(localPeerId: ByteArray, remotePeerId: ByteArray): String {
        val (first, second) = if (compareUnsigned(localPeerId, remotePeerId) <= 0) {
            localPeerId to remotePeerId
        } else {
            remotePeerId to localPeerId
        }
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(first)
            update(second)
            digest()
        }
        val value = ByteBuffer.wrap(digest, 0, 4).int.toLong() and 0xFFFF_FFFFL
        // ASCII digits in every locale, the desktop displays the same code
        return String.format(Locale.ROOT, "%06d", value % 1_000_000)
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) {
                return diff
            }
        }
        return a.size - b.size
    }

    private fun parse(payload: ByteArray): JSONObject = try {
        JSONObject(String(payload, Charsets.UTF_8))
    } catch (e: JSONException) {
        throw ProtocolException("Message is not valid JSON.")
    }

    private fun decodeFiles(json: JSONObject, incremental: Boolean): List<ManifestFile> {
        val array = json.optJSONArray("files") ?: throw ProtocolException("Manifest without files.")
        val files = (0 until array.length()).map { i ->
            val entry = array.optJSONObject(i) ?: throw ProtocolException("Invalid manifest entry $i.")
            val name = entry.optString("name")
            if (name.isEmpty() || !entry.has("size")) {
                throw ProtocolException("Invalid manifest entry $i.")
            }
            val size = try {
                entry.getLong("size")
            } catch (e: JSONException) {
                throw ProtocolException("Invalid size in manifest entry $i.")
            }
            if (size < 0) {
                throw ProtocolException("Negative size in manifest entry $i.")
            }
            ManifestFile(name, size)
        }
        if (incremental) {
            files.firstOrNull { !it.name.startsWith(FILE_PREFIX_INCR) }?.let {
                throw ProtocolException("Unexpected file '${it.name}' for an incremental synchronization.")
            }
        } else if (files.size != 1 || files[0].name != FILE_NAME_ALL) {
            throw ProtocolException("A full synchronization expects exactly $FILE_NAME_ALL.")
        }
        if (files.map { it.name }.toSet().size != files.size) {
            throw ProtocolException("Duplicate file names in manifest.")
        }
        return files
    }

    private fun JSONObject.toFrame(): ByteArray = frame(toString().toByteArray(Charsets.UTF_8))
}
