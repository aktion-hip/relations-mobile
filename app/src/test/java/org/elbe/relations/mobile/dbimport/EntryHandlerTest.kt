package org.elbe.relations.mobile.dbimport

import org.elbe.relations.mobile.model.Term
import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import java.util.*

class EntryHandlerTest {

    @Test
    fun testTermEntryHandler() {
        var handler = SubTermHandler()
        assertFalse(handler.isListening())

        // Title
        handler.initializeField("Title", createAttributes("STITLE", "String"))
        assertTrue(handler.isListening())

        handler.append("The Title".toCharArray())

        assertFalse(handler.testEndField("SomeThing"))
        assertTrue(handler.testEndField("Title"))

        // Created
        handler.initializeField("Created", createAttributes("DTCREATION", "Timestamp"))
        handler.append("2018-08-07 19:51:00.0".toCharArray())
        assertFalse(handler.testEndField("Modified"))
        assertTrue(handler.testEndField("Created"))

        // Modified
        handler.initializeField("Modified", createAttributes("DTMUTATION", "Timestamp"))
        handler.append("2018-08-07 22:32:50.193".toCharArray())
        assertTrue(handler.testEndField("Modified"))

        // Text
        handler.initializeField("Text", createAttributes("STEXT", "String"))
        handler.append("This is a text with ".toCharArray())
        handler.appendStartNode("i", createAttributes("fldVal", "typeVal"))
        handler.append("some italic".toCharArray())
        handler.appendEndNode("i")
        handler.append(" content contained!".toCharArray())
        assertTrue(handler.testEndField("Text"))

        // ID
        handler.initializeField("ID", createAttributes("TERMID", "Long"))
        handler.append("42".toCharArray())
        assertTrue(handler.testEndField("ID"))

        // test created item
        val  term = handler.createEntry()
        assertEquals("The Title", term.getTitle())
        assertEquals("This is a text with <i field=\"fldVal\" type=\"typeVal\">some italic</i> content contained!", term.text)
        assertEquals(42, term.getId())
        assertEquals("Tue Aug 07 19:51:00 CEST 2018", term.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 CEST 2018", term.getMutationDate().toString())
    }

    private fun createAttributes(fieldValue: String, typeValue: String): Attributes {
        val attr = AttributesImpl()
        attr.addAttribute("", "", "field", "", fieldValue)
        attr.addAttribute("", "", "type", "", typeValue)
        return attr
    }


//    ---

    class SubTermHandler: EntryHandler() {
        fun createEntry(): Term {
            return Term(mMap["TERMID"]!!.getValue().toLong(),
                    mMap["STITLE"]!!.getValue(),
                    mMap["STEXT"]!!.getValue(),
                    Date(mMap["DTCREATION"]!!.getValue().toLong()),
                    Date(mMap["DTMUTATION"]!!.getValue().toLong()))
        }

        override fun createEntryToDB(type: Int): IEntryToDB? {
            return null
        }
    }

}