package org.elbe.relations.mobile.dbimport

import android.util.Log
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Base class for DB import (data synchronization).
 */
abstract class AbstractDBImport(factory: IndexWriterFactory): DefaultHandler() {
    private val ROOT = "RelationsExport"
    protected val TAG = "DBImport"

    private val mIndexWriter: IndexWriter by lazy { factory.createIndexWriter() }
    private var mCanImport = false
    private var mNumberToImport = 0
    private lateinit var mProgress: (Int, Int) -> Unit

    protected fun getIndexWriter(): IndexWriter {
        return mIndexWriter
    }

    fun setProgress(progress: (Int, Int) -> Unit): AbstractDBImport {
        mProgress = progress
        return this
    }

    protected fun sendProgress() {
        mProgress(1, mNumberToImport)
    }

    override fun endDocument() {
        mIndexWriter.commit()
        mIndexWriter.close()
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        if (qName == ROOT) {
            mCanImport = true
            mNumberToImport = attributes?.getValue("countAll")?.toInt() ?: 0
            sendProgress()
            return
        }
        if (mCanImport) {
            startElementHandling(qName, attributes)
            Log.v(TAG, "Parsing start element - end of element handling.")
        }
    }

    abstract fun startElementHandling(qName: String?, attributes: Attributes?)

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (qName == ROOT) {
            mCanImport = false
            return
        }
        if (mCanImport) {
            endElementHandling(qName)
            Log.v(TAG, "Parsing end element - end of element handling.")
        }
    }

    abstract fun endElementHandling(qName: String?)

}