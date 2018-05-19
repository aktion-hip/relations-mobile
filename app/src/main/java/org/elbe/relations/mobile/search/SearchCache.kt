package org.elbe.relations.mobile.search

import org.elbe.relations.mobile.model.MinItem

/**
 * The cache for Lucene search results (singleton).
 */
object SearchCache {
    private var mResult: List<MinItem>? = null

    fun setResult(result: List<MinItem>) {
        mResult = result
    }

    fun getResult(): List<MinItem>? {
        return mResult
    }

    fun clear() {
        mResult = null
    }

    fun isEmpty(): Boolean {
        return mResult?.isEmpty() ?: false
    }
}