package org.elbe.relations.mobile.util

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.model.Item
import org.elbe.relations.mobile.model.MinItem
import org.elbe.relations.mobile.model.Type

/**
 * Helper class to retrieve the list of all items of a type (Term, Text, Person)
 */
class RetrieveListHelper {
    private var relDB: RelationsDataBase
    private var dbThread: HandlerThread

    constructor(context: Context, threadId: String) {
        dbThread = HandlerThread("relationsDb_$threadId")
        dbThread.start()
        relDB = RelationsDataBase.getInstance(context)!!
    }

    /**
     * Returns the list of all items of the specified type.
     */
    fun getListOf(type: Type) : List<Item> {
        when(type) {
            Type.TERM -> return relDB.termDAO()?.getAll()
            Type.TEXT -> return relDB.textDAO()?.getAll()
            Type.PERSON -> return relDB.personDAO()?.getAll()
        }
    }

    /**
     * Number of items in the database.
     */
    fun getCountOfAllItems(): Int {
        return relDB.termDAO().getCount() + relDB.textDAO().getCount() + relDB.personDAO().getCount()
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun run(task: Runnable) {
        val handler = Handler(dbThread.looper)
        handler.post(task)
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun quit(): Boolean {
        return dbThread?.quitSafely()
    }

    /**
     * Retrieves the full item from a mini item.
     *
     * @param item: MinItem
     * @return Item
     */
    fun getItem(item: MinItem): Item {
        if (item is Item) {
            return item
        }
        when (item.getType()) {
            Type.TERM -> return relDB.termDAO()?.findById(item.getId())
            Type.TEXT -> return relDB.textDAO()?.findById(item.getId())
            Type.PERSON -> return relDB.personDAO()?.findById(item.getId())
        }
    }

}