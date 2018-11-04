package org.elbe.relations.mobile.util

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.model.Item
import org.elbe.relations.mobile.model.MinItem

/**
 * Helper class to handle an item's related items.
 */
class RelationsHelper(context: Context?)     {
    private var mRelDB: RelationsDataBase
    private var mDbThread: HandlerThread = HandlerThread("relatedDbThread")

    init {
        mDbThread.start()
        mRelDB = RelationsDataBase.getInstance(context)!!
    }

    /**
     * Retrieves the specified item's related items.
     * Note: this method has to be run in the method <code>run</code>
     *
     * @param item MinItem
     * @return List<Item>
     */
    fun getRelated(item: MinItem) : List<Item> {
        val items: MutableList<Item> = mutableListOf<Item>()
        items.addAll(mRelDB.relationTerm1DAO().getTermsOf(item.getId(), item.getType().value))
        items.addAll(mRelDB.relationTerm2DAO().getTermsOf(item.getId(), item.getType().value))
        items.addAll(mRelDB.relationText1DAO().getTextsOf(item.getId(), item.getType().value))
        items.addAll(mRelDB.relationText2DAO().getTextsOf(item.getId(), item.getType().value))
        items.addAll(mRelDB.relationPerson1DAO().getPersonsOf(item.getId(), item.getType().value))
        items.addAll(mRelDB.relationPerson2DAO().getPersonsOf(item.getId(), item.getType().value))
        return items.sorted()
    }

    /**
     * Quit the database thread. Should be called before releasing the instance.
     */
    fun quit(): Boolean {
        return mDbThread.quitSafely()
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun run(task: Runnable) {
        val handler = Handler(mDbThread.looper)
        handler.post(task)
    }

}