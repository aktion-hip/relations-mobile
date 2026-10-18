package org.elbe.relations.mobile.p2p

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

private val IP4_TCP = Regex("^/ip4/(\\d{1,3}(?:\\.\\d{1,3}){3})/tcp/\\d{1,5}$")

/**
 * Finds this device's address on the local (WiFi) network and ranks a computer's addresses,
 * see design.md of the change reverse-p2p-roles (Decision 5).
 */
object LocalAddress {

    /**
     * An address of a network interface.
     *
     * @param prefixLength Int the network prefix length, e.g. 24 for 255.255.255.0
     */
    data class Address(val address: InetAddress, val prefixLength: Int)

    /**
     * The relevant properties of a network interface (NetworkInterface is final and hard to fake).
     */
    data class Interface(val name: String, val isUp: Boolean, val isLoopback: Boolean, val addresses: List<Address>)

    /**
     * This device's address on the local network.
     */
    data class Local(val address: Inet4Address, val prefixLength: Int) {
        /** @return Boolean true if the other address is in the same subnet */
        fun sameSubnet(other: Inet4Address): Boolean {
            val bits = prefixLength.coerceIn(0, 32)
            if (bits == 0) {
                return true
            }
            val mask = (-1L shl (32 - bits)).toInt()
            return (toInt(address) and mask) == (toInt(other) and mask)
        }

        private fun toInt(address: Inet4Address): Int =
                address.address.fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 0xff) }
    }

    /**
     * @param interfaces List<Interface> the device's network interfaces
     * @return Local? the first site-local IPv4 address of an interface that is up, preferring wlan*, null if none
     */
    fun pick(interfaces: List<Interface>): Local? {
        val candidates = interfaces.filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
        return candidates.firstNotNullOfOrNull { nic ->
            nic.addresses.firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress }
                    ?.let { Local(it.address as Inet4Address, it.prefixLength) }
        }
    }

    /**
     * @return Local? this device's current local-network address
     */
    fun current(): Local? = pick(NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { nic ->
        Interface(nic.name, nic.isUp, nic.isLoopback, nic.interfaceAddresses.map { Address(it.address, it.networkPrefixLength.toInt()) })
    })

    /**
     * Orders a computer's addresses for dialing: this device's subnet first, then other site-local addresses, then the rest.
     * Loopback and link-local addresses are dropped, loopback ones only if this device's address is not a loopback one (tests).
     *
     * @param addresses List<String> e.g. /ip4/192.168.1.10/tcp/47112, other forms are dropped
     * @param local Local? this device's address, null if unknown
     * @return List<String> the addresses to try, in this order
     */
    fun rank(addresses: List<String>, local: Local?): List<String> {
        val candidates = addresses.distinct().mapNotNull { text ->
            val ip = IP4_TCP.matchEntire(text)?.groupValues?.get(1) ?: return@mapNotNull null
            val octets = ip.split('.').map { it.toInt() }
            if (octets.any { it > 255 }) {
                return@mapNotNull null
            }
            val address = InetAddress.getByAddress(ByteArray(4) { octets[it].toByte() }) as Inet4Address
            val loopback = address.isLoopbackAddress && local?.address?.isLoopbackAddress != true
            if (loopback || address.isLinkLocalAddress || address.isAnyLocalAddress) null else text to address
        }
        return candidates.sortedBy { (_, address) ->
            when {
                local != null && local.sameSubnet(address) -> 0
                address.isSiteLocalAddress -> 1
                else -> 2
            }
        }.map { it.first }
    }
}
