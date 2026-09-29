package org.elbe.relations.mobile.p2p

import org.elbe.relations.mobile.p2p.PeerProtocol.ManifestFile
import org.elbe.relations.mobile.p2p.PeerProtocol.ProtocolException
import java.io.OutputStream

/**
 * Reassembles the byte chunks received from the stream (arbitrary boundaries) into control frames and files.
 *
 * Until expectFiles() is called, the bytes are control frames (4-byte big-endian length + JSON). Afterwards the
 * bytes are the raw content of the manifest's files, one after the other. Bytes beyond the last file are an error.
 *
 * @param maxFrame Int the maximal JSON size of a control frame
 * @param onFrame (ByteArray) -> Unit receives each complete frame's JSON, may call expectFiles()
 * @param onFileComplete (ManifestFile) -> Unit called when a file has been received completely (its sink is closed)
 */
class StreamDecoder(private val maxFrame: Int = PeerProtocol.MAX_FRAME,
                    private val onFrame: (ByteArray) -> Unit,
                    private val onFileComplete: (ManifestFile) -> Unit) {
    private val mHeader = ByteArray(PeerProtocol.HEADER_SIZE)
    private var mHeaderLength = 0
    private var mFrame: ByteArray? = null
    private var mFrameLength = 0

    private var mFiles: List<ManifestFile>? = null
    private var mSink: (ManifestFile) -> OutputStream = { throw IllegalStateException() }
    private var mFileIndex = 0
    private var mFileReceived = 0L
    private var mOutput: OutputStream? = null

    /** True once all announced files have been received. */
    val isComplete: Boolean
        get() = mFiles?.let { mFileIndex >= it.size } ?: false

    /**
     * Switches to receiving the files, e.g. from within onFrame() for the manifest.
     *
     * @param files List<ManifestFile> the files in the order they are sent
     * @param sink (ManifestFile) -> OutputStream opens the target of a file
     */
    fun expectFiles(files: List<ManifestFile>, sink: (ManifestFile) -> OutputStream) {
        check(mFiles == null) { "Files are already expected." }
        mFiles = files
        mSink = sink
        completeEmptyFiles()
    }

    /**
     * @param chunk ByteArray the received bytes
     * @throws ProtocolException for an oversized frame or bytes beyond the announced files
     */
    fun feed(chunk: ByteArray) {
        var offset = 0
        while (offset < chunk.size) {
            offset += if (mFiles == null) feedFrame(chunk, offset) else feedFile(chunk, offset)
        }
    }

    /** Closes the currently open file, e.g. after an abort. */
    fun close() {
        mOutput?.close()
        mOutput = null
    }

    private fun feedFrame(chunk: ByteArray, offset: Int): Int {
        if (mHeaderLength < mHeader.size) {
            val count = minOf(mHeader.size - mHeaderLength, chunk.size - offset)
            System.arraycopy(chunk, offset, mHeader, mHeaderLength, count)
            mHeaderLength += count
            if (mHeaderLength == mHeader.size) {
                val length = java.nio.ByteBuffer.wrap(mHeader).int.toLong() and 0xFFFF_FFFFL
                if (length > maxFrame) {
                    throw ProtocolException("Frame of $length bytes exceeds $maxFrame bytes.")
                }
                mFrame = ByteArray(length.toInt())
                mFrameLength = 0
                if (length == 0L) {
                    deliverFrame()
                }
            }
            return count
        }
        val frame = mFrame!!
        val count = minOf(frame.size - mFrameLength, chunk.size - offset)
        System.arraycopy(chunk, offset, frame, mFrameLength, count)
        mFrameLength += count
        if (mFrameLength == frame.size) {
            deliverFrame()
        }
        return count
    }

    private fun deliverFrame() {
        val frame = mFrame!!
        mFrame = null
        mHeaderLength = 0
        onFrame(frame)
    }

    private fun feedFile(chunk: ByteArray, offset: Int): Int {
        val files = mFiles!!
        if (mFileIndex >= files.size) {
            throw ProtocolException("Received more data than announced in the manifest.")
        }
        val file = files[mFileIndex]
        val output = mOutput ?: mSink(file).also { mOutput = it }
        val count = minOf(file.size - mFileReceived, (chunk.size - offset).toLong()).toInt()
        output.write(chunk, offset, count)
        mFileReceived += count
        if (mFileReceived == file.size) {
            completeFile()
            completeEmptyFiles()
        }
        return count
    }

    private fun completeFile() {
        val file = mFiles!![mFileIndex]
        (mOutput ?: mSink(file)).close()
        mOutput = null
        mFileIndex++
        mFileReceived = 0
        onFileComplete(file)
    }

    private fun completeEmptyFiles() {
        val files = mFiles!!
        while (mFileIndex < files.size && files[mFileIndex].size == 0L) {
            completeFile()
        }
    }
}
