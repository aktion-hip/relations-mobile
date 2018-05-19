package org.elbe.relations.mobile.model

import org.elbe.relations.mobile.util.UniqueID

/**
 * The item implementation for a Lucene search query.
 */
class RetrievedItem(uniqueID: UniqueID, title: String): MinItem {
    val mUniqueID = uniqueID
    val mTitle = title

    override fun getId(): Long {
        return mUniqueID.id
    }

    override fun getType(): Type {
        return mUniqueID.type
    }

    override fun getTitle() = mTitle

}