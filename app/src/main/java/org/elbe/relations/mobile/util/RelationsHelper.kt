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
class RelationsHelper {
    private var relDB: RelationsDataBase
    private var dbThread: HandlerThread = HandlerThread("relatedDbThread")

    constructor(context: Context) {
        dbThread.start()
        relDB = RelationsDataBase.getInstance(context)!!
    }

    /**
     * Retrieves the specified item's related items.
     * Note: this method has to be run in the method <code>run</code>
     *
     * @param item MinItem
     * @return List<Item>
     */
    fun getRelated(item: MinItem) : List<Item> {
        var items: MutableList<Item> = mutableListOf<Item>()
        items.addAll(relDB.relationTerm1DAO().getTermsOf(item.getId(), item.getType().value))
        items.addAll(relDB.relationTerm2DAO().getTermsOf(item.getId(), item.getType().value))
        items.addAll(relDB.relationText1DAO().getTextsOf(item.getId(), item.getType().value))
        items.addAll(relDB.relationText2DAO().getTextsOf(item.getId(), item.getType().value))
        items.addAll(relDB.relationPerson1DAO().getPersonsOf(item.getId(), item.getType().value))
        items.addAll(relDB.relationPerson2DAO().getPersonsOf(item.getId(), item.getType().value))
        return items.sorted()
    }

    /**
     * Quit the database thread. Should be called before releasing the instance.
     */
    fun quit(): Boolean {
        return dbThread?.quitSafely()
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun run(task: Runnable) {
        val handler = Handler(dbThread.looper)
        handler.post(task)
    }

}