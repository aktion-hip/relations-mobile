@file:Suppress("NAME_SHADOWING")
package org.elbe.relations.mobile.util

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.search.IndexReaderFactory
import java.text.NumberFormat
import java.util.*

/**
 * Helper class to retrieve the entry counts for the about info view.
 */
class AboutInfoHelper(context: Context?) {
    private val mNumberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    private var mRelationsDB: RelationsDataBase
    private var mDbThread: HandlerThread = HandlerThread("aboutInfoHelper")
    private var mCounts = Counts(0,0,0,0)
    private var mNumberOfIndexed = 0
    private var mVersion = ""

    init {
        mDbThread.start()
        mRelationsDB = RelationsDataBase.getInstance(context)!!
        context?.let {context ->
            mNumberOfIndexed = IndexReaderFactory.getNumberOfIndexed(context)
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            mVersion = pkgInfo.versionName
        }
    }

    fun initialize() {
        mCounts = Counts(
                mRelationsDB.termDAO().getCount(),
                mRelationsDB.textDAO().getCount(),
                mRelationsDB.personDAO().getCount(),
                mRelationsDB.relationDAO().getCount()
        )
    }

    fun getCountTotal(): String {
        return mNumberFormat.format(mCounts.total())
    }

    fun getCountTerms(): String {
        return mNumberFormat.format(mCounts.terms)
    }

    fun getCountTexts(): String {
        return mNumberFormat.format(mCounts.texts)
    }

    fun getCountPersons(): String {
        return mNumberFormat.format(mCounts.persons)
    }

    fun getCountRelations(): String {
        return mNumberFormat.format(mCounts.relations)
    }

    fun getNumberOfIndexed(): String {
        return mNumberFormat.format(mNumberOfIndexed)
    }

    fun getVersion(): String {
        return mVersion
    }

    /**
     * Runs the specified task (i.e. access to the database) in the background thread.
     */
    fun run(task: Runnable) {
        val handler = Handler(mDbThread.looper)
        handler.post(task)
    }

    /**
     * Quit the database thread. Should be called before releasing the instance.
     */
    fun quit(): Boolean {
        return mDbThread.quitSafely()
    }

//    ---

    private data class Counts(val terms: Int, val texts: Int, val persons: Int, val relations: Int) {

        fun total(): Int {
            return terms + texts + persons + relations
        }
    }

}