package org.elbe.relations.mobile.util

import org.elbe.relations.mobile.model.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueIDTest {

    @Test
    fun testUniqueID() {
        val uniqueID = UniqueID(Type.TEXT, 42)
        assertEquals(Type.TEXT, uniqueID.type)
        assertEquals(42, uniqueID.id)
    }

    @Test
    fun testUniqueIDString() {
        val uniqueID = UniqueID("2:42")
        assertEquals(Type.TEXT, uniqueID.type)
        assertEquals(42, uniqueID.id)
    }

    @Test
    fun testString() {
        assertEquals("3:4321", UniqueID.getUniqueID(Type.PERSON, 4321))
    }

}