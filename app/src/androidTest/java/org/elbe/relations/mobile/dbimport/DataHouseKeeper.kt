package org.elbe.relations.mobile.dbimport

import org.elbe.relations.mobile.data.RelationsDataBase
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

class DataHouseKeeper {

    companion object {
        fun clear(db: RelationsDataBase) {
            db.termDAO().clear()
            db.textDAO().clear()
            db.personDAO().clear()
            db.relationDAO().clear()
        }

        fun createAttributes(fieldValue: String, typeValue: String): Attributes {
            val attr = AttributesImpl()
            attr.addAttribute("", "", "field", "", fieldValue)
            attr.addAttribute("", "", "type", "", typeValue)
            return attr
        }

        fun fillHandler(handler: EntryHandler, title: String, created: String, modified: String): EntryHandler {
            // Title
            fillField(handler, title, "Title", "STITLE", "String")

            // Created
            fillField(handler, created, "Created", "DTCREATION", "Timestamp")

            // Modified
            fillField(handler, modified, "Modified", "DTMUTATION", "Timestamp")

            return handler
        }

        fun fillField(handler: EntryHandler, value: String, fieldName: String, attName: String, attType: String): EntryHandler {
            handler.initializeField(fieldName, DataHouseKeeper.createAttributes(attName, attType))
            handler.append(value.toCharArray())
            handler.testEndField(fieldName)
            return handler
        }
    }
}