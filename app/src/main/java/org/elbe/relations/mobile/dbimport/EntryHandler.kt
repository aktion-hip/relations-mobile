package org.elbe.relations.mobile.dbimport

import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.model.Person
import org.elbe.relations.mobile.model.Relation
import org.elbe.relations.mobile.model.Term
import org.elbe.relations.mobile.model.Text
import org.xml.sax.Attributes
import java.util.*

/**
 * Module to handle the parsed entries, e.g. insert or update Terms/Texts/Persons/Relations.
 */

interface IEntryToDB {
    fun setToDB(relDB: RelationsDataBase, writer: IndexWriter)
}

abstract class EntryHandler {
    private val mValue = StringBuilder()
    private var mField: EntryField? = null
    protected val mMap = mutableMapOf<String, EntryField>()

    fun initializeField(name: String?, attributes: Attributes?) {
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

    fun testEndField(name: String?): Boolean {
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

    fun appendStartNode(name: String?, attributes: Attributes?) {
        mValue.append(String.format("<%s%s>", name, processAttributes(attributes)))
    }

    private fun processAttributes(attributes: Attributes?): StringBuilder {
        val atts = StringBuilder()
        attributes?.let {
            for (i in 0 until it.length) {
                atts.append(String.format(" %s=\"%s\"", it.getQName(i), it.getValue(i)))
            }
        }
        return atts
    }

    fun isListening(): Boolean {
        return mField != null
    }

    fun append(chars: CharArray) {
        mValue.append(chars)
    }

    fun appendEndNode(name: String?): Boolean {
        if (mField == null) {
            return false
        }
        mValue.append(String.format("</%s>", name))
        return true
    }

    abstract fun createEntryToDB(type: Int): IEntryToDB?
}

open class TermHandler: EntryHandler() {
    fun createEntry(): Term {
        return Term(mMap["TERMID"]!!.getValue().toLong(),
                mMap["STITLE"]!!.getValue(),
                mMap["STEXT"]!!.getValue(),
                Date(mMap["DTCREATION"]!!.getValue().toLong()),
                Date(mMap["DTMUTATION"]!!.getValue().toLong()))

    }

    override fun createEntryToDB(type: Int): IEntryToDB? {
        return when (type) {
            1 -> TermEntryToDBInsert(this)
            2 -> TermEntryToDBUpdate(this)
            else -> null
        }
    }

}

class TermEntryToDBInsert(handler: TermHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        val term = mHandler.createEntry()
        relDB.termDAO().insert(term)
        term.indexContent(writer)
    }
}

class TermEntryToDBUpdate(handler: TermHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        val term = mHandler.createEntry()
        relDB.termDAO().update(term)
        term.indexContent(writer)
    }
}

open class TextHandler: EntryHandler() {
    fun createEntry(): Text {
        return Text(mMap["TEXTID"]!!.getValue().toLong(),
                mMap["STITLE"]!!.getValue(),
                mMap["STEXT"]!!.getValue(),
                mMap["SAUTHOR"]!!.getValue(),
                mMap["SCOAUTHORS"]!!.getValue(),
                mMap["SSUBTITLE"]!!.getValue(),
                mMap["SYEAR"]!!.getValue(),
                mMap["SPUBLICATION"]!!.getValue(),
                mMap["SPAGES"]!!.getValue(),
                mMap["NVOLUME"]!!.getValue().toInt(),
                mMap["NNUMBER"]!!.getValue().toInt(),
                mMap["SPUBLISHER"]!!.getValue(),
                mMap["SPLACE"]!!.getValue(),
                mMap["NTYPE"]!!.getValue().toInt(),
                Date(mMap["DTCREATION"]!!.getValue().toLong()),
                Date(mMap["DTMUTATION"]!!.getValue().toLong()))
    }

    override fun createEntryToDB(type: Int): IEntryToDB? {
        return when (type) {
            1 -> TextEntryToDBInsert(this)
            2 -> TextEntryToDBUpdate(this)
            else -> null
        }
    }
}

class TextEntryToDBInsert(handler: TextHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        val text = mHandler.createEntry()
        relDB.textDAO().insert(text)
        text.indexContent(writer)
    }
}

class TextEntryToDBUpdate(handler: TextHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        val text = mHandler.createEntry()
        relDB.textDAO().update(text)
        text.indexContent(writer)
    }
}

open class PersonHandler: EntryHandler() {
    fun createEntry(): Person {
        return Person(mMap["PERSONID"]!!.getValue().toLong(),
                mMap["SNAME"]!!.getValue(),
                mMap["SFIRSTNAME"]!!.getValue(),
                mMap["STEXT"]!!.getValue(),
                mMap["SFROM"]!!.getValue(),
                mMap["STO"]!!.getValue(),
                Date(mMap["DTCREATION"]!!.getValue().toLong()),
                Date(mMap["DTMUTATION"]!!.getValue().toLong()))
    }

    override fun createEntryToDB(type: Int): IEntryToDB? {
        return when (type) {
            1 -> PersonEntryToDBInsert(this)
            2 -> PersonEntryToDBUpdate(this)
            else -> null
        }
    }
}

class PersonEntryToDBInsert(handler: PersonHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        val person = mHandler.createEntry()
        relDB.personDAO().insert(person)
        person.indexContent(writer)
    }
}

class PersonEntryToDBUpdate(handler: PersonHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        val person = mHandler.createEntry()
        relDB.personDAO().update(person)
        person.indexContent(writer)
    }
}

open class RelationHandler: EntryHandler() {
    fun createEntry(): Relation {
        return Relation(mMap["RELATIONID"]!!.getValue().toLong(),
                mMap["NTYPE1"]!!.getValue().toInt(),
                mMap["NITEM1"]!!.getValue().toLong(),
                mMap["NTYPE2"]!!.getValue().toInt(),
                mMap["NITEM2"]!!.getValue().toLong())
    }

    override fun createEntryToDB(type: Int): IEntryToDB? {
        return when (type) {
            1 -> RelationEntryToDBInsert(this)
            2 -> RelationEntryToDBUpdate(this)
            else -> null
        }
    }
}

class RelationEntryToDBInsert(handler: RelationHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        relDB.relationDAO().insert(mHandler.createEntry())
    }
}

class RelationEntryToDBUpdate(handler: RelationHandler): IEntryToDB {
    private val mHandler = handler

    override fun setToDB(relDB: RelationsDataBase, writer: IndexWriter) {
        relDB.relationDAO().update(mHandler.createEntry())
    }
}
