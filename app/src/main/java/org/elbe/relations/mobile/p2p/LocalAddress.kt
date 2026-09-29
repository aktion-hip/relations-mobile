package org.elbe.relations.mobile.p2p

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Finds this device's address on the local (WiFi) network, see design.md (Decision 3).
 */
object LocalAddress {

    /**
     * The relevant properties of a network interface (NetworkInterface is final and hard to fake).
     */
    data class Interface(val name: String, val isUp: Boolean, val isLoopback: Boolean, val addresses: List<InetAddress>)

    /**
     * @param interfaces List<Interface> the device's network interfaces
     * @return Inet4Address? the first site-local IPv4 address of an interface that is up, preferring wlan*, null if none
     */
    fun pick(interfaces: List<Interface>): Inet4Address? {
        val candidates = interfaces.filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
        return candidates.firstNotNullOfOrNull { nic ->
            nic.addresses.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }
        }
    }

    /**
     * @return Inet4Address? this device's current local-network address
     */
    fun current(): Inet4Address? = pick(NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { nic ->
        Interface(nic.name, nic.isUp, nic.isLoopback, nic.inetAddresses.toList())
    })
}
