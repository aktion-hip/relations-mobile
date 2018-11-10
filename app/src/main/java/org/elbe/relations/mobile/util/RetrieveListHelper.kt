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
class RetrieveListHelper(context: Context, threadId: String) {
    private var mRelDB: RelationsDataBase
    private var mDbThread= HandlerThread("relationsDb_$threadId")

    init {
        mDbThread.start()
        mRelDB = RelationsDataBase.getInstance(context)!!
    }

    /**
     * Returns the list of all items of the specified type.
     */
    fun getListOf(type: Type) : List<Item> {
        return when(type) {
            Type.TERM -> mRelDB.termDAO().getAll()
            Type.TEXT -> mRelDB.textDAO().getAll()
            Type.PERSON -> mRelDB.personDAO().getAll()
            Type.RELATION -> emptyList()
        }
    }

    /**
     * Number of items in the database.
     */
    fun getCountOfAllItems(): Int {
        return mRelDB.termDAO().getCount() + mRelDB.textDAO().getCount() + mRelDB.personDAO().getCount()
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun run(task: Runnable) {
        val handler = Handler(mDbThread.looper)
        handler.post(task)
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun quit(): Boolean {
        return mDbThread.quitSafely()
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
        return when (item.getType()) {
            Type.TERM -> mRelDB.termDAO().findById(item.getId())
            Type.TEXT -> mRelDB.textDAO().findById(item.getId())
            Type.PERSON -> mRelDB.personDAO().findById(item.getId())
            Type.RELATION -> throw IllegalArgumentException("Type Relation is not allowed!")
        }
    }

}