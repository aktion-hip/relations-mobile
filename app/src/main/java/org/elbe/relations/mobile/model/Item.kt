package org.elbe.relations.mobile.model

import android.content.res.Resources
import org.apache.lucene.document.*
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.search.INDX_CONTENT_FULL
import org.elbe.relations.mobile.search.INDX_TITLE
import org.elbe.relations.mobile.search.INDX_UNIQUE_ID
import org.elbe.relations.mobile.search.Indexable
import org.elbe.relations.mobile.util.UniqueID
import java.io.Serializable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

val DATE_FORMAT : DateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm")

/**
 * Interface for all items.
 *
 * Created by lbenno on 02.03.2018.
 */
interface Item: MinItem, Comparable<Item>, Indexable {

    /**
     * The item's text to be displayed on the details page.
     */
    fun getDetailText(r: Resources): String

    /**
     * Returns "Created: {0,date}, {0,time}; Modified: {1,date}, {1,time}."
     */
    fun getCreated(r: Resources) : String =  "${r.getString(R.string.item_created)}: ${DATE_FORMAT.format(getCreationDate())}; " +
            "${r.getString(R.string.item_modified)}: ${DATE_FORMAT.format(getMutationDate())}"

    /**
     * Abstract method returning the item's creation date.
     */
    fun getCreationDate() : Date

    /**
     * Abstract method returning the item's mutation date.
     */
    fun getMutationDate() : Date

    /**
     * Method to sort list of items.
     */
    override fun compareTo(other: Item): Int {
        return getTitle().compareTo(other.getTitle())
    }

//    --- helper methods for the search index ---

    fun getUniqueID(): String {
        return UniqueID.getUniqueID(getType(), getId())
    }

    fun getFieldUniqueID(): Field {
        return StringField(INDX_UNIQUE_ID, getUniqueID(), Field.Store.YES)
    }

    fun getFieldItemType(): Field {
        return StringField("itemType", getType().value.toString(), Field.Store.YES)
    }

    fun getFieldItemID(): Field {
        return StringField("itemID", getId().toString(), Field.Store.YES)
    }

    fun getFieldTitle(text: String): Field {
        val field = TextField(INDX_TITLE, text, Field.Store.YES)
        field.setBoost(2f)
        return field
    }

    fun getFieldText(text: FullTextHelper): Field {
        return TextField(INDX_CONTENT_FULL, text.toString(), Field.Store.NO)
    }

    fun addFieldCreatedModified(document: Document) {
        document.add(StringField("itemDateCreated", DateTools.timeToString(getCreationDate().time, DateTools.Resolution.DAY), Field.Store.YES))
        document.add(StringField("itemDateModified", DateTools.timeToString(getMutationDate().time, DateTools.Resolution.DAY), Field.Store.YES))
    }

    fun getFullTextHelper(text: String): FullTextHelper {
        return FullTextHelper(text)
    }

//    ---

    class FullTextHelper(text: String) {
        private val fullText = StringBuilder(text).append(' ')

        fun add(additional: String?): FullTextHelper {
            additional?.let {
                fullText.append(additional).append(' ')
            }
            return this
        }

        override fun toString(): String {
            return fullText.toString()
        }
    }

}