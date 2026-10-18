package org.elbe.relations.mobile.p2p

import io.libp2p.core.PeerId
import java.net.Inet4Address
import java.net.InetAddress

private val PATTERN = Regex("^/ip4/(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/tcp/(\\d{1,5})/p2p/([1-9A-HJ-NP-Za-km-z]+)/?$")

/**
 * The Relations desktop application's address as entered in debug builds, e.g. /ip4/10.0.2.2/tcp/47112/p2p/12D3KooW...
 *
 * @param address Inet4Address the computer's address
 * @param port Int the TCP port the computer listens on
 * @param peerId String the computer's peer ID (base58)
 */
data class PeerAddress(val address: Inet4Address, val port: Int, val peerId: String) {

    /** @return String the multiaddress to dial */
    override fun toString(): String = "/ip4/${address.hostAddress}/tcp/$port/p2p/$peerId"

    companion object {
        /**
         * Parses without name resolution, i.e. usable on the main thread.
         *
         * @param text String e.g. /ip4/10.0.2.2/tcp/47112/p2p/12D3KooW...
         * @return PeerAddress? null if the text is not an IPv4 TCP multiaddress with a port from 1 to 65535 and a valid peer ID
         */
        fun parse(text: String): PeerAddress? {
            val groups = PATTERN.matchEntire(text.trim())?.groupValues ?: return null
            val octets = groups.subList(1, 5).map { it.toInt() }
            if (octets.any { it > 255 }) {
                return null
            }
            val port = groups[5].toInt()
            if (port !in 1..65535) {
                return null
            }
            val peerId = try {
                PeerId.fromBase58(groups[6]).toBase58()
            } catch (e: Exception) {
                return null
            }
            val address = InetAddress.getByAddress(ByteArray(4) { octets[it].toByte() }) as Inet4Address
            return PeerAddress(address, port, peerId)
        }
    }
}
