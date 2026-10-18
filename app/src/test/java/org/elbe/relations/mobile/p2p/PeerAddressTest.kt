package org.elbe.relations.mobile.p2p

import io.libp2p.core.PeerId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PeerAddressTest {
    private val dir: File = Files.createTempDirectory("peerAddressTest").toFile()
    private val id: String = PeerId.fromPubKey(Libp2pHost.loadOrCreateKey(File(dir, "desktop.key")).publicKey()).toBase58()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun testValid() {
        val address = PeerAddress.parse("/ip4/10.0.2.2/tcp/47112/p2p/$id")!!
        assertEquals("10.0.2.2", address.address.hostAddress)
        assertEquals(47112, address.port)
        assertEquals(id, address.peerId)
        assertEquals("/ip4/10.0.2.2/tcp/47112/p2p/$id", address.toString())
        // surrounding whitespace and a trailing slash
        assertEquals(address, PeerAddress.parse("  /ip4/10.0.2.2/tcp/47112/p2p/$id/ \n"))
    }

    @Test
    fun testInvalid() {
        listOf("/ip4/192.168.1.10/tcp/47112", "/ip4/192.168.1.10/tcp/47112/p2p/1111", "/ip4/192.168.1.10/tcp/47112/p2p/0OIl",
                "/ip4/example.com/tcp/47112/p2p/$id", "/ip4/300.1.1.1/tcp/47112/p2p/$id", "/ip4/192.168.1.10/tcp/0/p2p/$id",
                "/ip4/192.168.1.10/tcp/70000/p2p/$id", "192.168.1.10:47112", "/ip6/::1/tcp/47112/p2p/$id",
                "/ip4/192.168.1.10/udp/47112/p2p/$id", "").forEach {
            assertNull(it, PeerAddress.parse(it))
        }
    }
}
