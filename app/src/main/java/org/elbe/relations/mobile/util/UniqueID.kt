package org.elbe.relations.mobile.util

import org.elbe.relations.mobile.model.Type
import java.io.Serializable

private val TEMPLATE = "%s:%s"

/**
 * Utility class for an item's unique ID that consists of item type and item ID.
 *
 * @param type: Type the item's type
 * @param id: Long the item's entry id
 */
class UniqueID(val type: Type, val id: Long): Serializable {

    constructor(uniqueID: String) : this(getType(uniqueID.substring(0,getIndex(uniqueID)).toInt()),
            uniqueID.substring(getIndex(uniqueID)+1).toLong()) {
    }

//    ---

    companion object {
        private fun getType(typeVal: Int): Type {
            Type.values().forEach {type ->
                if (type.value == typeVal) {
                    return type
                }
            }
            return Type.TERM
        }

        private fun getIndex(value: String): Int {
            return value.indexOf(":")
        }

        fun getUniqueID(type: Type, id: Long): String {
            return String.format(TEMPLATE, type.value.toString(), id)
        }
    }
}