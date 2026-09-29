package org.elbe.relations.mobile.p2p

import org.elbe.relations.mobile.p2p.LocalAddress.Interface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class LocalAddressTest {
    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    private val loopback = Interface("lo", isUp = true, isLoopback = true, addresses = listOf(ip("127.0.0.1")))
    private val mobile = Interface("rmnet0", isUp = true, isLoopback = false, addresses = listOf(ip("10.64.1.5")))
    private val wifi = Interface("wlan0", isUp = true, isLoopback = false, addresses = listOf(ip("fe80::1"), ip("192.168.1.23")))

    @Test
    fun testPrefersWlan() {
        assertEquals(ip("192.168.1.23"), LocalAddress.pick(listOf(loopback, mobile, wifi)))
    }

    @Test
    fun testSkipsLoopbackAndDownInterfaces() {
        val down = wifi.copy(isUp = false)
        assertEquals(ip("10.64.1.5"), LocalAddress.pick(listOf(loopback, down, mobile)))
        assertNull(LocalAddress.pick(listOf(loopback, down)))
    }

    @Test
    fun testNoSiteLocalIpv4() {
        val public = Interface("wlan0", isUp = true, isLoopback = false, addresses = listOf(ip("fe80::1"), ip("8.8.8.8")))
        assertNull(LocalAddress.pick(listOf(public)))
        assertNull(LocalAddress.pick(emptyList()))
    }

    @Test
    fun testOtherSiteLocalRanges() {
        listOf("10.0.0.2", "172.16.5.4", "192.168.0.10").forEach {
            val nic = Interface("wlan0", isUp = true, isLoopback = false, addresses = listOf(ip(it)))
            assertEquals(ip(it), LocalAddress.pick(listOf(nic)))
        }
    }
}
