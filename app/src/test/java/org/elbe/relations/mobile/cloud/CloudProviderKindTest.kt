package org.elbe.relations.mobile.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudProviderKindTest {
    private val available = listOf("dropbox", "ms_azure", "p2p")

    @Test
    fun testFromIdSupported() {
        assertEquals(CloudProviderKind.DROPBOX, CloudProviderKind.fromId("dropbox"))
        assertEquals(CloudProviderKind.MS_AZURE, CloudProviderKind.fromId("ms_azure"))
        assertEquals(CloudProviderKind.P2P, CloudProviderKind.fromId("p2p"))
    }

    @Test
    fun testFromIdUnsupported() {
        assertNull(CloudProviderKind.fromId("google_drive"))
        // the unreleased Nearby source of the first implementation
        assertNull(CloudProviderKind.fromId("nearby"))
        assertNull(CloudProviderKind.fromId(""))
        assertNull(CloudProviderKind.fromId(null))
    }

    @Test
    fun testSelectableIdKeepsSupported() {
        assertEquals("ms_azure", CloudProviderKind.selectableId("ms_azure", available))
        assertEquals("dropbox", CloudProviderKind.selectableId("dropbox", available))
        assertEquals("p2p", CloudProviderKind.selectableId("p2p", available))
    }

    @Test
    fun testSelectableIdP2pNotAvailable() {
        // e.g. after a rollback that removes the peer-to-peer source
        assertEquals("dropbox", CloudProviderKind.selectableId("p2p", listOf("dropbox", "ms_azure")))
    }

    @Test
    fun testSelectableIdFallsBackToDefault() {
        assertEquals("dropbox", CloudProviderKind.selectableId("google_drive", available))
        assertEquals("dropbox", CloudProviderKind.selectableId("", available))
        assertEquals("dropbox", CloudProviderKind.selectableId(null, available))
    }

}
