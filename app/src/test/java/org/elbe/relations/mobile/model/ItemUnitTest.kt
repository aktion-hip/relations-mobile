package org.elbe.relations.mobile.model

import android.content.res.Resources
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.R
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when` as _when
import org.mockito.runners.MockitoJUnitRunner
import java.util.*

/**
 * Created by lbenno on 02.03.2018.
 */
@RunWith(MockitoJUnitRunner::class)
class ItemUnitTest {

    @Mock
    lateinit var res: Resources

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        _when(res.getString(R.string.item_created)).thenReturn("Created")
        _when(res.getString(R.string.item_modified)).thenReturn("Modified")
    }

    @Test
    fun getCreatedTest() {
        Assert.assertEquals("Created: 26.04.1970, 18:46; Modified: 26.04.1970, 21:33", TestItem().getCreated(res))
    }
}

class TestItem : Item {
    private val millis: Long = 10000000000
    private val delta: Long = 10000000

    override fun getId(): Long {
        return 1L
    }

    override fun getTitle(): String {
        return ""
    }

    override fun getCreationDate(): Date {
        return Date(millis)
    }

    override fun getMutationDate(): Date {
        return  Date(millis + delta)
    }

    override fun getDetailText(r: Resources): String {
        return "Details"
    }

    override fun getType(): Type {
        return Type.TERM
    }

    override fun indexContent(writer: IndexWriter) {
        // nothing to do
    }

}