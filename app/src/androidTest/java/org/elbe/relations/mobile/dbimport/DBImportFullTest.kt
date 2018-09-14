package org.elbe.relations.mobile.dbimport

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import junit.framework.Assert
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.data.TermDAO
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.AttributesImpl
import java.util.*
import javax.xml.parsers.SAXParserFactory

@RunWith(AndroidJUnit4::class)
class DBImportFullTest {

    private val mProgress: (Int, Int) -> Unit = { n1: Int, n2: Int -> }
    private val mContext = InstrumentationRegistry.getTargetContext()
    private lateinit var mFactory: IndexWriterFactory

    private lateinit var mDb: RelationsDataBase
    private lateinit var mTermDao: TermDAO

    @Before
    fun setUp() {
        mFactory = IndexWriterFactory(mContext.applicationContext, mContext.applicationContext.resources)

        mDb = Room.databaseBuilder(mContext.applicationContext, RelationsDataBase::class.java, "Relations.db").build()
        mTermDao = mDb.termDAO()
    }

    @After
    fun tearDown() {
        DataHouseKeeper.clear(mDb)
        mDb.close()
    }

    @Test
    fun testElementHandling() {
        Assert.assertEquals(0, mTermDao.getCount())

        val dbImport = DBImportFull(mContext, mFactory, mProgress)

        // create inserter mFactory
        dbImport.startElementHandling("TermEntries", null)
        Assert.assertEquals(0, mTermDao.getCount())

        // create inserter
        dbImport.startElementHandling("TermEntry", null)
        Assert.assertEquals(0, mTermDao.getCount())

        // insert values into term table
        dbImport.startElementHandling("Modified", DataHouseKeeper.createAttributes("DTMUTATION", "Timestamp"))
        dbImport.characters("2018-08-07 22:32:50.193".toCharArray(), 0, 23)
        dbImport.endElementHandling("Modified")

        dbImport.startElementHandling("Title", DataHouseKeeper.createAttributes("STITLE", "String"))
        dbImport.characters("A Test Item".toCharArray(), 0, 11)
        dbImport.endElementHandling("Title")

        dbImport.startElementHandling("Text", DataHouseKeeper.createAttributes("STEXT", "String"))
        dbImport.characters("An item for testing purposes.".toCharArray(), 0, 29)
        dbImport.endElementHandling("Text")

        dbImport.startElementHandling("Created", DataHouseKeeper.createAttributes("DTCREATION", "Timestamp"))
        dbImport.characters("2018-08-07 22:32:50.193".toCharArray(), 0, 23)
        dbImport.endElementHandling("Created")

        dbImport.startElementHandling("ID", DataHouseKeeper.createAttributes("TERMID", "Long"))
        dbImport.characters("5".toCharArray(), 0, 1)
        dbImport.endElementHandling("ID")

        Assert.assertEquals(0, mTermDao.getCount())
        dbImport.endElementHandling("TermEntry")
        Assert.assertEquals(1, mTermDao.getCount())

        dbImport.endElementHandling("TermEntries")

        // test created term
        val term = mTermDao.findById(5)
        Assert.assertEquals("A Test Item", term.getTitle())
        Assert.assertEquals("An item for testing purposes.", term.text)
        Assert.assertEquals(GregorianCalendar(2018,7,7,22,32,50).timeInMillis + 193,
                term.getCreationDate().time)
    }

    @Test
    fun testParse() {
        val textDAO = mDb.textDAO()
        val personDAO = mDb.personDAO()
        val relationDAO = mDb.relationDAO()

        Assert.assertEquals(0, mTermDao.getCount())
        Assert.assertEquals(0, textDAO.getCount())
        Assert.assertEquals(0, personDAO.getCount())
        Assert.assertEquals(0, relationDAO.getCount())

        val parser = SAXParserFactory.newInstance().newSAXParser().xmlReader
        parser.contentHandler = DBImportFull(mContext, mFactory, mProgress)
        parser.parse(InputSource(DBImportFullTest::class.java.classLoader.getResourceAsStream("relations_all.xml")))

        Assert.assertEquals(5, mTermDao.getCount())
        Assert.assertEquals(0, textDAO.getCount())
        Assert.assertEquals(1, personDAO.getCount())
        Assert.assertEquals(6, relationDAO.getCount())

        val term = mTermDao.findById(4)
        Assert.assertEquals("Neuer <b>Begriff</b>.", term.text)
    }

}