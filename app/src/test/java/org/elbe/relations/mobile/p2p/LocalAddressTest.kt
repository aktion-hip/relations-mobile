package org.elbe.relations.mobile.p2p

import org.elbe.relations.mobile.p2p.LocalAddress.Address
import org.elbe.relations.mobile.p2p.LocalAddress.Interface
import org.elbe.relations.mobile.p2p.LocalAddress.Local
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class LocalAddressTest {
    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    private fun ip4(text: String): Inet4Address = ip(text) as Inet4Address

    private fun nic(name: String, vararg addresses: String, isUp: Boolean = true, isLoopback: Boolean = false) =
            Interface(name, isUp, isLoopback, addresses.map { Address(ip(it), if (it.contains(':')) 64 else 24) })

    private val loopback = nic("lo", "127.0.0.1", isLoopback = true)
    private val mobile = nic("rmnet0", "10.64.1.5")
    private val wifi = nic("wlan0", "fe80::1", "192.168.1.23")

    @Test
    fun testPrefersWlan() {
        assertEquals(Local(ip4("192.168.1.23"), 24), LocalAddress.pick(listOf(loopback, mobile, wifi)))
    }

    @Test
    fun testSkipsLoopbackAndDownInterfaces() {
        val down = wifi.copy(isUp = false)
        assertEquals(ip("10.64.1.5"), LocalAddress.pick(listOf(loopback, down, mobile))?.address)
        assertNull(LocalAddress.pick(listOf(loopback, down)))
    }

    @Test
    fun testNoSiteLocalIpv4() {
        assertNull(LocalAddress.pick(listOf(nic("wlan0", "fe80::1", "8.8.8.8"))))
        assertNull(LocalAddress.pick(emptyList()))
    }

    @Test
    fun testOtherSiteLocalRanges() {
        listOf("10.0.0.2", "172.16.5.4", "192.168.0.10").forEach {
            assertEquals(ip(it), LocalAddress.pick(listOf(nic("wlan0", it)))?.address)
        }
    }

    @Test
    fun testSameSubnet() {
        val local = Local(ip4("192.168.1.23"), 24)
        assertTrue(local.sameSubnet(ip4("192.168.1.10")))
        assertFalse(local.sameSubnet(ip4("192.168.2.10")))
        assertTrue(Local(ip4("10.1.2.3"), 8).sameSubnet(ip4("10.200.0.1")))
        assertTrue(Local(ip4("10.1.2.3"), 0).sameSubnet(ip4("8.8.8.8")))
    }

    @Test
    fun testRankWindowsLikeAddresses() {
        val local = Local(ip4("192.168.1.23"), 24)
        val announced = listOf("/ip4/172.20.0.1/tcp/47112", "/ip4/127.0.0.1/tcp/47112", "/ip4/169.254.3.4/tcp/47112",
                "/ip4/82.110.58.123/tcp/47112", "/ip4/192.168.1.10/tcp/47112", "/ip6/::1/tcp/47112", "/ip4/192.168.1.10/tcp/47112")
        assertEquals(listOf("/ip4/192.168.1.10/tcp/47112", "/ip4/172.20.0.1/tcp/47112", "/ip4/82.110.58.123/tcp/47112"),
                LocalAddress.rank(announced, local))
        // without a local address: site-local first
        assertEquals(listOf("/ip4/172.20.0.1/tcp/47112", "/ip4/192.168.1.10/tcp/47112", "/ip4/82.110.58.123/tcp/47112"),
                LocalAddress.rank(announced, null))
        // on a loopback address (tests) loopback addresses are kept
        assertEquals(listOf("/ip4/127.0.0.1/tcp/47112"), LocalAddress.rank(listOf("/ip4/127.0.0.1/tcp/47112"), Local(ip4("127.0.0.1"), 8)))
    }
}
