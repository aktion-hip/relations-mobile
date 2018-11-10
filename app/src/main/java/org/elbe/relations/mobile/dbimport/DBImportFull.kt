package org.elbe.relations.mobile.dbimport

import android.content.Context
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.xml.sax.Attributes

/**
 * The SAX handler class to import the data into the database (i.e. full data synchronization).
 */
class DBImportFull(context: Context, factory: IndexWriterFactory): AbstractDBImport(factory) {

    private var mRelationsDB = RelationsDataBase.getInstance(context)!!
    private var mHandlerFactory: HandlerFactory? = null
    private var mEntryHandler: EntryHandler? = null

    override fun startDocument() {
        // TODO: remove?
        mRelationsDB.termDAO().clear()
        mRelationsDB.textDAO().clear()
        mRelationsDB.personDAO().clear()
        mRelationsDB.relationDAO().clear()
    }

    override fun startElementHandling(qName: String?, attributes: Attributes?) {
        mEntryHandler?.let { inserter ->
            // e.g. field in "Term" or nodes in a field's text
            if (inserter.isListening()) {
                inserter.appendStartNode(qName, attributes)
                return
            }
            inserter.initializeField(qName, attributes)
            return
        }
        mHandlerFactory?.let {
            mEntryHandler = mHandlerFactory?.createHandler()
            return
        }
        HandlerFactory.values().forEach { factory ->
            if (factory.checkType(qName)) {
                mHandlerFactory = factory
            }
        }
    }

    override fun endElementHandling(qName: String?) {
        mHandlerFactory?.let { factory ->
            mEntryHandler?.let { handler ->
                if (handler.testEndField(qName)) {
                    return
                }
                if (handler.appendEndNode(qName)) {
                    return
                }
            }
            if (factory.checkNode(qName)) {
                mEntryHandler?.let { handler ->
                    sendProgress()
                    handler.createEntryToDB(1)?.setToDB(mRelationsDB, getIndexWriter())
                }
                mEntryHandler = null
                return
            }
        }
        HandlerFactory.values().forEach { factory ->
            if (factory.checkType(qName)) {
                getIndexWriter().commit()
                mHandlerFactory = null
            }
        }
    }

    override fun characters(chars: CharArray?, start: Int, length: Int) {
        mEntryHandler?.let { inserter ->
            val target = CharArray(length)
            System.arraycopy(chars, start, target, 0, length)
            inserter.append(target)
        }
    }

}