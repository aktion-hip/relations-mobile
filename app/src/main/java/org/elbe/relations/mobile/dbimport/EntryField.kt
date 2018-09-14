package org.elbe.relations.mobile.dbimport

import java.text.SimpleDateFormat

/**
 * Module of classes to store the content (i.e. the fiels) of the parsed entries.
 */

interface EntryField {
    fun getFieldName(): String
    fun setValue(value: StringBuilder)
    fun getValue(): String
}

abstract class AbstractField(fieldName: String?): EntryField {
    private val mFldName = fieldName
    private var mValue = ""

    override fun getFieldName(): String {
        return mFldName ?: ""
    }

    protected fun setValue(input: String) {
        mValue = input.trim()
    }

    override fun getValue(): String {
        return mValue
    }
}

class StringField(fieldName: String?): AbstractField(fieldName) {
    override fun setValue(input: StringBuilder) {
        setValue(XMLImporter.prepareForImport(input.toString().trim()))
    }
}

class NumberField(fieldName: String?): AbstractField(fieldName) {
    override fun setValue(input: StringBuilder) {
        setValue(input.toString().trim())
    }
}

class IntegerField(fieldName: String?): AbstractField(fieldName) {
    override fun setValue(input: StringBuilder) {
        setValue(input.toString().trim())
    }
}

class TimestampField(fieldName: String?): AbstractField(fieldName) {
    private val pattern = "yyyy-MM-dd HH:mm:ss.SSS"

    // convert timestamp as string (with pattern '2007-03-07 00:28:33.0') to long, i.e. milliseconds since January 1, 1970, 00:00:00 GMT
    override fun setValue(input: StringBuilder) {
        setValue(SimpleDateFormat(pattern).parse(input.toString().trim()).time.toString())
    }
}