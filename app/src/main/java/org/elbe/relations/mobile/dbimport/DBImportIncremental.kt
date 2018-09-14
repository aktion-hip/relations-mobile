package org.elbe.relations.mobile.dbimport

import android.content.Context
import android.util.Log
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.xml.sax.Attributes

/**
 * The SAX handler class to import the data incrementally into the database (i.e. incremental data synchronization).
 */
class DBImportIncremental(context: Context, factory: IndexWriterFactory): AbstractDBImport(factory) {
    private var mRelationsDB = RelationsDataBase.getInstance(context)!!
    private val mEventProcessor = EventProcessor()
    private var mHandler: EntryHandler? = null

    override fun startElementHandling(qName: String?, attributes: Attributes?) {
        mHandler?.let {handler ->
            if (handler.isListening()) {
                handler.appendStartNode(qName, attributes)
                return
            }
            handler.initializeField(qName, attributes)
            return
        }

        if (mEventProcessor.isEventHandling()) {
            mHandler = mEventProcessor.createEventHandler(qName)
            return
        }
        if (mEventProcessor.isActive()) {
            if (!mEventProcessor.isEventNode(qName)) {
                mEventProcessor.initializeProcessing(qName, attributes)
            }
            return
        }
        mEventProcessor.startActivation(qName)
    }

    override fun endElementHandling(qName: String?) {
        mHandler?.let {handler ->
            if (handler.testEndField(qName)) {
                return
            }
            if (mEventProcessor.isEndEventNode(qName)) {
                mEventProcessor.setHandler(mHandler)
                mHandler = null
            } else {
                handler.appendEndNode(qName)
            }
            return
        }

        if (mEventProcessor.isEndEventNode(qName)) {
            return
        }
        if (mEventProcessor.isActive()) {
            mEventProcessor.testEndProcessing(qName)
        }
        mEventProcessor.stopActivation(qName, mRelationsDB, getIndexWriter())
    }

    override fun characters(chars: CharArray?, start: Int, length: Int) {
        var target = CharArray(length)
        System.arraycopy(chars, start, target, 0, length)

        mHandler?.let {handler ->
            handler.append(target)
            return
        }
        if (mEventProcessor.isProcessing()) {
            mEventProcessor.append(target)
        }
    }

}