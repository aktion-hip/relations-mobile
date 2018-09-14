package org.elbe.relations.mobile.dbimport

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import junit.framework.Assert
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.model.Relation
import org.elbe.relations.mobile.model.Term
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.junit.After
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xml.sax.InputSource
import javax.xml.parsers.SAXParserFactory

@RunWith(AndroidJUnit4::class)
class DBImportIncrementalTest {

    private val mProgress: (Int, Int) -> Unit = { n1: Int, n2: Int -> }
    private val mContext = InstrumentationRegistry.getTargetContext()
    private lateinit var mFactory: IndexWriterFactory

    private lateinit var mDb: RelationsDataBase

    @Before
    fun setUp() {
        mFactory = IndexWriterFactory(mContext.applicationContext, mContext.applicationContext.resources)
        mDb = Room.databaseBuilder(mContext.applicationContext, RelationsDataBase::class.java, "Relations.db").build()

        // we expect two entries 1:5 and 0:14 to exist in the database
        mDb.termDAO().insert(Term(5, "Title", "Text"))
        mDb.relationDAO().insert(Relation(14, 1,1,1,2))
    }

    @After
    fun tearDown() {
        DataHouseKeeper.clear(mDb)
        mDb.close()
    }

    @Test
    fun testParse() {
        val termDAO = mDb.termDAO()
        val textDAO = mDb.textDAO()
        val personDAO = mDb.personDAO()
        val relationDAO = mDb.relationDAO()

        Assert.assertEquals(1, termDAO.getCount())
        Assert.assertEquals(0, textDAO.getCount())
        Assert.assertEquals(0, personDAO.getCount())
        Assert.assertEquals(1, relationDAO.getCount())

        // we update 1:5, delete 0:14 and create 0:17 and 0:18
        val parser = SAXParserFactory.newInstance().newSAXParser().xmlReader
        parser.contentHandler = DBImportIncremental(mContext, mFactory, mProgress)
        parser.parse(InputSource(DBImportIncrementalTest::class.java.classLoader.getResourceAsStream("relations_delta.xml")))

        Assert.assertEquals(1, termDAO.getCount())
        Assert.assertEquals(0, textDAO.getCount())
        Assert.assertEquals(0, personDAO.getCount())
        Assert.assertEquals(2, relationDAO.getCount())

        val term = mDb.termDAO().findById(5)
        assertEquals("CCC", term.getTitle())
        assertEquals("ccc  <u>ccc</u> ccc", term.text)

        val rel1 = mDb.relationDAO().findById(17)
        assertEquals(5, rel1.item1)
        assertEquals(1, rel1.item2)

        val rel2 = mDb.relationDAO().findById(18)
        assertEquals(1, rel2.item1)
        assertEquals(4, rel2.item2)
    }

}