package org.elbe.relations.mobile.model

import java.io.Serializable

/**
 * Minimal version of an item.
 */
interface MinItem: Serializable {

    /**
     * The item's unique id.
     */
    fun getId() : Long

    /**
     * Returns the item's type.
     */
    fun getType(): Type

    /**
     * The item's title.
     */
    fun getTitle() : String

}