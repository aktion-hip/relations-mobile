package org.elbe.relations.mobile.dbimport

import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.model.Type
import org.elbe.relations.mobile.util.UniqueID
import org.xml.sax.Attributes

/**
 * Factory for event processors modelled as enum.
 */
class EventProcessor {
    private val ENTRY_ROOT = "EventStoreEntry"

    private var mHandler: EntryHandler? = null
    private val mValue = StringBuilder()
    private var mField: EntryField? = null
    private val mMap = mutableMapOf<String, EntryField>()

    private var mActive = false
    private var mEventHandling = false

    fun isActive(): Boolean {
        return mActive
    }

    fun startActivation(qName: String?) {
        mActive = (qName == ENTRY_ROOT)
    }

    /**
     * When the parser reaches the end tag of EventStoreEntry, the parsed values are evaluated and the affected entry in the db is actualized
     */
    fun stopActivation(qName: String?, relDB: RelationsDataBase, writer: IndexWriter) {
        if (qName == ENTRY_ROOT) {
            val uniqueId = mMap["SUNIQUEID"]!!.getValue()
            val type = mMap["NTYPE"]!!.getValue().toInt()
            when (type) {
                1 -> mHandler?.createEntryToDB(type)?.setToDB(relDB, writer)
                2 -> mHandler?.createEntryToDB(type)?.setToDB(relDB, writer)
                3 -> delete(UniqueID(uniqueId), relDB)
            }
            mActive = false
        }
    }

    private fun delete(uniqueID: UniqueID, relDB: RelationsDataBase) {
        when (uniqueID.type) {
            Type.TERM -> relDB.termDAO().delete(uniqueID.id)
            Type.TEXT -> relDB.textDAO().delete(uniqueID.id)
            Type.PERSON -> relDB.personDAO().delete(uniqueID.id)
            Type.RELATION -> relDB.relationDAO().delete(uniqueID.id)
        }
    }

    fun initializeProcessing(name: String?, attributes: Attributes?) {
        attributes?.let {
            val type = attributes.getValue("type")
            val fieldName = attributes.getValue("field")
            if (type == "String") {
                mField = StringField(name)
                mMap[fieldName] = mField!!
            }
            if (type == "Number" || type == "Long") {
                mField = NumberField(name)
                mMap[fieldName] = mField!!
            }
            if (type == "Integer") {
                mField = IntegerField(name)
                mMap[fieldName] = mField!!
            }
            if (type == "Timestamp") {
                mField = TimestampField(name)
                mMap[fieldName] = mField!!
            }
        }
    }

    fun isProcessing(): Boolean {
        return mField != null;
    }

    fun testEndProcessing(name: String?): Boolean {
        if (mField == null) {
            return false
        }
        if (!mField?.getFieldName().equals(name)) {
            return false
        }
        // e.g. "</ID>"
        mField?.setValue(mValue)
        // reset
        mField = null
        mValue.setLength(0)
        mValue.trimToSize()
        return true
    }

    fun append(chars: CharArray) {
        mValue.append(chars)
    }

    fun isEventNode(name: String?): Boolean {
        if (name == "Event") {
            mEventHandling = true
        }
        return mEventHandling
    }

    fun isEndEventNode(name: String?): Boolean {
        if (name == "Event") {
            mEventHandling = false
            return true
        }
        return false
    }

    fun isEventHandling(): Boolean {
        return mEventHandling
    }

    fun createEventHandler(name: String?): EntryHandler? {
        HandlerFactory.values().forEach {factory ->
            if (factory.checkNode(name)) {
                return factory.createHandler()
            }
        }
        return null
    }

    fun setHandler(handler: EntryHandler?) {
        mHandler = handler
        mEventHandling = false
    }

//    ---

    enum class EventProcessor {
        CREATE,
        UPDATE,
        DELETE;
    }

}