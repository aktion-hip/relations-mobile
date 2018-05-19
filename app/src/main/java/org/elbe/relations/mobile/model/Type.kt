package org.elbe.relations.mobile.model

import org.elbe.relations.mobile.R

/**
 * Enum for item types, containing the type's value and the type's icon.
 */
enum class Type(val value: Int, val icon: Int) {
    TERM(1, R.drawable.ic_term),
    TEXT(2, R.drawable.ic_text),
    PERSON(3, R.drawable.ic_person)
}