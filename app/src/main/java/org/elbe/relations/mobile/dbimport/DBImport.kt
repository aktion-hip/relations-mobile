package org.elbe.relations.mobile.dbimport

import android.content.Context
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.model.*
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.text.SimpleDateFormat
import java.util.*

/**
 * The SAX handler class to import the data into the database.
 */
class DBImport(context: Context, factory: IndexWriterFactory, prog: (Int, Int)-> Unit): DefaultHandler() {
    private val ROOT = "RelationsExport"

    private val indexWriter: IndexWriter by lazy { factory.createIndexWriter() }
    private val mProg = prog
    private var canImport = false
    private var relDB = RelationsDataBase.getInstance(context)!!
    private var inserterFactory: InserterFactory? = null
    private var inserter: IEntryInserter? = null
    private var numberToImport = 0

    override fun startDocument() {
        relDB.termDAO().clear()
        relDB.textDAO().clear()
        relDB.personDAO().clear()
        relDB.relationDAO().clear()
    }

    override fun endDocument() {
        indexWriter.close()
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (ROOT.equals(qName)) {
            canImport = true
            numberToImport = attributes?.getValue("countAll")?.toInt() ?: 0
            mProg(1, numberToImport)
            return
        }
        if (canImport) {
            inserter?.let {
                // e.g. field in "Term" or nodes in a field's text
                if (it.isListening()) {
                    it.appendStartNode(qName, attributes)
                    return
                }
                it.initializeField(qName, attributes)
                return
            }
            inserterFactory?.let {
                inserter = inserterFactory?.createInserter(qName)
                return
            }
            InserterFactory.values().forEach { factory ->
                if (factory.checkType(qName)) {
                    inserterFactory = factory
                }
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (ROOT.equals(qName)) {
            canImport = false
            return
        }
        if (canImport) {
            inserterFactory?.let {factory ->
                inserter?.let {
                    if (it.testEndField(qName)) {
                        return
                    }
                    it.appendEndNode(qName)
                }
                if (factory.checkNode(qName)) {
                    inserter?.let {
                        mProg(1, numberToImport)
                        it.insert(relDB, indexWriter)
                    }
                    inserter = null
                    return
                }
            }
            InserterFactory.values().forEach { factory ->
                if (factory.checkType(qName)) {
                    inserterFactory = null
                }
            }
        }
    }

    override fun characters(chars: CharArray?, start: Int, length: Int) {
        inserter?.let {
            var target = CharArray(length)
            System.arraycopy(chars, start, target, 0, length)
            it.append(target)
        }
    }

//    ---

    interface IEntryInserter {
        fun insert(relDB: RelationsDataBase, writer: IndexWriter)
        fun initializeField(name: String?, attributes: Attributes?)
        fun testEndField(name: String?): Boolean
        fun append(chars: CharArray)
        fun appendStartNode(name: String?, attributes: Attributes?)
        fun appendEndNode(name: String?)
        fun isListening(): Boolean
    }

    abstract class Inserter() {
        private val value = StringBuilder()
        private var field: InsertField? = null
        protected val map = mutableMapOf<String, InsertField>()

        fun initializeField(name: String?, attributes: Attributes?) {
            attributes?.let {
                val type = attributes.getValue("type")
                val fieldName = attributes.getValue("field")
                if ("String".equals(type)) {
                    field = StringField(name)
                    map[fieldName] = field!!
                }
                if ("Number".equals(type) || "Long".equals(type)) {
                    field = NumberField(name)
                    map[fieldName] = field!!
                }
                if ("Integer".equals(type)) {
                    field = IntegerField(name)
                    map[fieldName] = field!!
                }
                if ("Timestamp".equals(type)) {
                    field = TimestampField(name)
                    map[fieldName] = field!!
                }
            }
        }

        fun testEndField(name: String?): Boolean {
            if (field == null) {
                return false
            }
            if (!field?.getFieldName().equals(name)) {
                return false
            }
            // e.g. "</ID>"
            field?.setValue(value)
            // reset
            field = null
            value.setLength(0)
            value.trimToSize()
            return true
        }

        fun appendStartNode(name: String?, attributes: Attributes?) {
            value.append(String.format("<%s%s>", name, processAttributes(attributes)))
        }

        private fun processAttributes(attributes: Attributes?): StringBuilder {
            val atts = StringBuilder()
            attributes?.let {
                for (i in 0..it.length) {
                    atts.append(String.format(" %s=\"%s\"", it.getQName(i), it.getValue(i)))
                }
            }
            return atts
        }

        fun isListening(): Boolean {
            return field != null
        }

        fun append(chars: CharArray) {
            value.append(chars)
        }

        fun appendEndNode(name: String?) {
            value.append(String.format("</%s>", name))
        }
    }

    class TermInserter: Inserter(), IEntryInserter {
        override fun insert(relDB: RelationsDataBase, writer: IndexWriter) {
            val term = Term(map["TERMID"]!!.getValue().toLong(),
                    map["STITLE"]!!.getValue(),
                    map["STEXT"]!!.getValue(),
                    Date(map["DTCREATION"]!!.getValue().toLong()),
                    Date(map["DTMUTATION"]!!.getValue().toLong()))
            relDB.termDAO().insert(term)
            term.indexContent(writer)
        }
    }

    class TextInserter: Inserter(), IEntryInserter {
        override fun insert(relDB: RelationsDataBase, writer: IndexWriter) {
            val text = Text(map["TEXTID"]!!.getValue().toLong(),
                    map["STITLE"]!!.getValue(),
                    map["STEXT"]!!.getValue(),
                    map["SAUTHOR"]!!.getValue(),
                    map["SCOAUTHORS"]!!.getValue(),
                    map["SSUBTITLE"]!!.getValue(),
                    map["SYEAR"]!!.getValue(),
                    map["SPUBLICATION"]!!.getValue(),
                    map["SPAGES"]!!.getValue(),
                    map["NVOLUME"]!!.getValue().toInt(),
                    map["NNUMBER"]!!.getValue().toInt(),
                    map["SPUBLISHER"]!!.getValue(),
                    map["SPLACE"]!!.getValue(),
                    map["NTYPE"]!!.getValue().toInt(),
                    Date(map["DTCREATION"]!!.getValue().toLong()),
                    Date(map["DTMUTATION"]!!.getValue().toLong()))
            relDB.textDAO().insert(text)
            text.indexContent(writer)
        }
    }

    class PersonInserter: Inserter(), IEntryInserter {
        override fun insert(relDB: RelationsDataBase, writer: IndexWriter) {
            val person = Person(map["PERSONID"]!!.getValue().toLong(),
                    map["SNAME"]!!.getValue(),
                    map["SFIRSTNAME"]!!.getValue(),
                    map["STEXT"]!!.getValue(),
                    map["SFROM"]!!.getValue(),
                    map["STO"]!!.getValue(),
                    Date(map["DTCREATION"]!!.getValue().toLong()),
                    Date(map["DTMUTATION"]!!.getValue().toLong()))
            relDB.personDAO().insert(person)
            person.indexContent(writer)
        }
    }

    class RelationInserter: Inserter(), IEntryInserter {
        override fun insert(relDB: RelationsDataBase, writer: IndexWriter) {
            val relation = Relation(map["RELATIONID"]!!.getValue().toLong(),
                    map["NTYPE1"]!!.getValue().toInt(),
                    map["NITEM1"]!!.getValue().toLong(),
                    map["NTYPE2"]!!.getValue().toInt(),
                    map["NITEM2"]!!.getValue().toLong())
            relDB.relationDAO().insert(relation)
        }
    }

//    --- field helpers

    interface InsertField {
        fun getFieldName(): String
        fun setValue(value: StringBuilder)
        fun getValue(): String
    }

    abstract class AbstractField(fieldName: String?): InsertField {
        private val fldName = fieldName
        private var value = ""

        override fun getFieldName(): String {
            return fldName ?: ""
        }

        protected fun setValue(input: String) {
            value = input
        }

        override fun getValue(): String {
            return value
        }
    }

    class StringField(fieldName: String?): AbstractField(fieldName) {

        override fun setValue(input: StringBuilder) {
            setValue(XMLImporter.prepareForImport(input.toString().trim()))
        }

    }

    class NumberField(fieldName: String?): AbstractField(fieldName) {
        override fun setValue(input: StringBuilder) {
            setValue(input.toString().trim())
        }
    }

    class IntegerField(fieldName: String?): AbstractField(fieldName) {
        override fun setValue(input: StringBuilder) {
            setValue(input.toString().trim())
        }

    }

    class TimestampField(fieldName: String?): AbstractField(fieldName) {
        private val pattern = "yyyy-MM-dd HH:mm:ss.SSS"

        // convert timestamp as string (with pattern '2007-03-07 00:28:33.0') to long, i.e. milliseconds since January 1, 1970, 00:00:00 GMT
        override fun setValue(input: StringBuilder) {
            setValue(SimpleDateFormat(pattern).parse(input.toString().trim()).time.toString())
        }
    }

}