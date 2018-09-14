package org.elbe.relations.mobile.dbimport

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

class EntryHandlerDBTest {

    private val mContext = InstrumentationRegistry.getTargetContext()
    private lateinit var mFactory: IndexWriterFactory
    private lateinit var mWriter: IndexWriter

    private lateinit var mDb: RelationsDataBase

    @Before
    fun setUp() {
        mDb = Room.databaseBuilder(mContext.applicationContext, RelationsDataBase::class.java, "Relations.db").build()
        mFactory = IndexWriterFactory(mContext.applicationContext, mContext.applicationContext.resources)
        mWriter = mFactory.createIndexWriter()
    }

    @After
    fun tearDown() {
        DataHouseKeeper.clear(mDb)
        mDb.close()
        mWriter.close()
    }

    @Test
    fun testTermHandling() {
        val createTerm = DataHouseKeeper.fillHandler(TermHandler(), "The Title", "2018-08-07 19:51:00.0", "2018-08-07 22:32:50.193")
        DataHouseKeeper.fillField(createTerm, "42", "ID", "TERMID", "Long")

        // text field
        createTerm.initializeField("Text", DataHouseKeeper.createAttributes("STEXT", "String"))
        createTerm.append("This is a text with ".toCharArray())
        createTerm.appendStartNode("i", DataHouseKeeper.createAttributes("fldVal", "typeVal"))
        createTerm.append("some italic".toCharArray())
        createTerm.appendEndNode("i")
        createTerm.append(" content contained!".toCharArray())
        createTerm.testEndField("Text")

        val termDAO = mDb.termDAO()
        assertEquals(0, termDAO.getCount())

        if (createTerm is TermHandler) {
            val termToDB = TermEntryToDBInsert(createTerm)
            termToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, termDAO.getCount())

        var term = termDAO.findById(42)
        assertEquals("The Title", term.getTitle())
        assertEquals("This is a text with <i field=\"fldVal\" type=\"typeVal\">some italic</i> content contained!", term.text)
        assertEquals("Tue Aug 07 19:51:00 GMT+00:00 2018", term.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 GMT+00:00 2018", term.getMutationDate().toString())

        val updateTerm = DataHouseKeeper.fillHandler(TermHandler(), "New Title", "2018-08-07 19:51:00.0", "2018-08-07 22:32:50.193")
        DataHouseKeeper.fillField(updateTerm, "42", "ID", "TERMID", "Long")
        DataHouseKeeper.fillField(updateTerm, "Updated item: new text.", "Text", "STEXT", "String")

        if (updateTerm is TermHandler) {
            val termToDB = TermEntryToDBUpdate(updateTerm)
            termToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, termDAO.getCount())

        term = termDAO.findById(42)
        assertEquals("New Title", term.getTitle())
        assertEquals("Updated item: new text.", term.text)
        assertEquals("Tue Aug 07 19:51:00 GMT+00:00 2018", term.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 GMT+00:00 2018", term.getMutationDate().toString())
    }

    @Test
    fun testPersonHandling() {
        val createPerson = fillPersonHandler(DataHouseKeeper.fillHandler(PersonHandler(), "", "2018-08-07 19:51:00.0", "2018-08-07 22:32:50.193"), "Riese", "Adam")
        DataHouseKeeper.fillField(createPerson, "101", "ID", "PERSONID", "Long")

        // text field
        createPerson.initializeField("Text", DataHouseKeeper.createAttributes("STEXT", "String"))
        createPerson.append("This is a text with ".toCharArray())
        createPerson.appendStartNode("i", DataHouseKeeper.createAttributes("fldVal", "typeVal"))
        createPerson.append("some italic".toCharArray())
        createPerson.appendEndNode("i")
        createPerson.append(" content contained!".toCharArray())
        createPerson.testEndField("Text")

        var personDAO = mDb.personDAO()
        assertEquals(0, personDAO.getCount())

        if (createPerson is PersonHandler) {
            val personToDB = PersonEntryToDBInsert(createPerson)
            personToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, personDAO.getCount())

        var person = personDAO.findById(101)
        assertEquals("Riese", person.name)
        assertEquals("Adam", person.firstname)
        assertEquals("This is a text with <i field=\"fldVal\" type=\"typeVal\">some italic</i> content contained!", person.text)
        assertEquals("Tue Aug 07 19:51:00 GMT+00:00 2018", person.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 GMT+00:00 2018", person.getMutationDate().toString())

        val updatePerson = fillPersonHandler(DataHouseKeeper.fillHandler(PersonHandler(), "", "2018-08-07 19:51:00.0", "2018-08-07 22:32:50.193"), "Doe", "Jane")
        DataHouseKeeper.fillField(updatePerson, "101", "ID", "PERSONID", "Long")
        DataHouseKeeper.fillField(updatePerson, "Updated item: new text.", "Text", "STEXT", "String")

        if (updatePerson is PersonHandler) {
            val persondToDB = PersonEntryToDBUpdate(updatePerson)
            persondToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, personDAO.getCount())

        person = personDAO.findById(101)
        assertEquals("Doe", person.name)
        assertEquals("Jane", person.firstname)
        assertEquals("Updated item: new text.", person.text)
        assertEquals("Tue Aug 07 19:51:00 GMT+00:00 2018", person.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 GMT+00:00 2018", person.getMutationDate().toString())
    }

    @Test
    fun testTextHandling() {
        val createText = fillTextHandler(DataHouseKeeper.fillHandler(TextHandler(), "New Text Item", "2018-08-07 19:51:00.0", "2018-08-07 22:32:50.193"), "Doe, Joe")
        DataHouseKeeper.fillField(createText, "123", "ID", "TEXTID", "Long")

        // text field
        createText.initializeField("Text", DataHouseKeeper.createAttributes("STEXT", "String"))
        createText.append("This is a text with ".toCharArray())
        createText.appendStartNode("i", DataHouseKeeper.createAttributes("fldVal", "typeVal"))
        createText.append("some italic".toCharArray())
        createText.appendEndNode("i")
        createText.append(" content contained!".toCharArray())
        createText.testEndField("Text")

        var textDAO = mDb.textDAO()
        assertEquals(0, textDAO.getCount())

        if (createText is TextHandler) {
            val textToDB = TextEntryToDBInsert(createText)
            textToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, textDAO.getCount())

        var text = textDAO.findById(123)

        assertEquals("New Text Item", text.getTitle())
        assertEquals("This is a text with <i field=\"fldVal\" type=\"typeVal\">some italic</i> content contained!", text.text)
        assertEquals("Doe, Joe", text.author)
        assertEquals("Tue Aug 07 19:51:00 GMT+00:00 2018", text.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 GMT+00:00 2018", text.getMutationDate().toString())

        val updateText = fillTextHandler(DataHouseKeeper.fillHandler(TextHandler(), "Updated Text Item", "2018-08-07 19:51:00.0", "2018-08-07 22:32:50.193"), "Riese, Adam")
        DataHouseKeeper.fillField(updateText, "123", "ID", "TEXTID", "Long")
        DataHouseKeeper.fillField(updateText, "Updated item: new text.", "Text", "STEXT", "String")

        if (updateText is TextHandler) {
            val textToDB = TextEntryToDBUpdate(updateText)
            textToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, textDAO.getCount())

        text = textDAO.findById(123)
        assertEquals("Updated Text Item", text.getTitle())
        assertEquals("Updated item: new text.", text.text)
        assertEquals("Riese, Adam", text.author)
        assertEquals("Tue Aug 07 19:51:00 GMT+00:00 2018", text.getCreationDate().toString())
        assertEquals("Tue Aug 07 22:32:50 GMT+00:00 2018", text.getMutationDate().toString())
    }

    @Test
    fun testRelationHandling() {
        val createRelation = RelationHandler()
        DataHouseKeeper.fillField(createRelation, "111", "ID", "RELATIONID", "Long")
        DataHouseKeeper.fillField(createRelation, "1", "", "NTYPE1", "Integer")
        DataHouseKeeper.fillField(createRelation, "42", "", "NITEM1", "Long")
        DataHouseKeeper.fillField(createRelation, "2", "", "NTYPE2", "Integer")
        DataHouseKeeper.fillField(createRelation, "21", "", "NITEM2", "Long")

        val relationDAO = mDb.relationDAO()
        assertEquals(0, relationDAO.getCount())

        if (createRelation is RelationHandler) {
            val relationToDB = RelationEntryToDBInsert(createRelation)
            relationToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, relationDAO.getCount())

        var relation = relationDAO.findById(111)
        assertEquals(1, relation.type1)
        assertEquals(42, relation.item1)
        assertEquals(2, relation.type2)
        assertEquals(21, relation.item2)

        val updateRelation = RelationHandler()
        DataHouseKeeper.fillField(updateRelation, "111", "ID", "RELATIONID", "Long")
        DataHouseKeeper.fillField(updateRelation, "1", "", "NTYPE1", "Integer")
        DataHouseKeeper.fillField(updateRelation, "42", "", "NITEM1", "Long")
        DataHouseKeeper.fillField(updateRelation, "1", "", "NTYPE2", "Integer")
        DataHouseKeeper.fillField(updateRelation, "66", "", "NITEM2", "Long")

        if (updateRelation is RelationHandler) {
            val relationToDB = RelationEntryToDBUpdate(updateRelation)
            relationToDB.setToDB(mDb, mWriter)
        }
        assertEquals(1, relationDAO.getCount())

        relation = relationDAO.findById(111)
        assertEquals(1, relation.type1)
        assertEquals(42, relation.item1)
        assertEquals(1, relation.type2)
        assertEquals(66, relation.item2)
    }

//    ---

    private fun fillPersonHandler(handler: EntryHandler, name: String, firstName: String): EntryHandler {
        DataHouseKeeper.fillField(handler, name, "Name", "SNAME", "String")
        DataHouseKeeper.fillField(handler, firstName, "Firstname", "SFIRSTNAME", "String")
        DataHouseKeeper.fillField(handler, "", "From", "SFROM", "String")
        DataHouseKeeper.fillField(handler, "", "To", "STO", "String")
        return handler
    }

    private fun fillTextHandler(handler: EntryHandler, author: String): EntryHandler {
        DataHouseKeeper.fillField(handler, author, "Author", "SAUTHOR", "String")
        DataHouseKeeper.fillField(handler, "", "CoAuthors", "SCOAUTHORS", "String")
        DataHouseKeeper.fillField(handler, "", "Subtitle", "SSUBTITLE", "String")
        DataHouseKeeper.fillField(handler, "", "Year", "SYEAR", "String")
        DataHouseKeeper.fillField(handler, "", "Publication", "SPUBLICATION", "String")
        DataHouseKeeper.fillField(handler, "", "Pages", "SPAGES", "String")
        DataHouseKeeper.fillField(handler, "1", "Volume", "NVOLUME", "Integer")
        DataHouseKeeper.fillField(handler, "2", "Number", "NNUMBER", "Integer")
        DataHouseKeeper.fillField(handler, "", "Publisher", "SPUBLISHER", "String")
        DataHouseKeeper.fillField(handler, "", "Place", "SPLACE", "String")
        DataHouseKeeper.fillField(handler, "1", "Type", "NTYPE", "String")
        return handler
    }

}