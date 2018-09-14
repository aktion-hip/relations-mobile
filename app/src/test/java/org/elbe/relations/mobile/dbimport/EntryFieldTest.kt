package org.elbe.relations.mobile.dbimport

import org.junit.Assert
import org.junit.Test

import org.junit.Assert.*

class EntryFieldTest {

    @Test
    fun testStringField() {
        val field = StringField("Text")
        field.setValue(StringBuilder("A string field's content."))

        Assert.assertEquals("Text", field.getFieldName())
        Assert.assertEquals("A string field's content.", field.getValue())
    }

    @Test
    fun testNumberField() {
        val field = NumberField("Number")
        field.setValue(StringBuilder("42"))

        Assert.assertEquals("Number", field.getFieldName())
        Assert.assertEquals("42", field.getValue())
    }

    @Test
    fun testIntegerField() {
        val field = IntegerField("Integer")
        field.setValue(StringBuilder("42"))

        Assert.assertEquals("Integer", field.getFieldName())
        Assert.assertEquals("42", field.getValue())
    }

    @Test
    fun testTimestampField() {
        val field = TimestampField("Timestamp")
        field.setValue(StringBuilder("2018-08-07 22:32:50.193"))

        Assert.assertEquals("Timestamp", field.getFieldName())
        Assert.assertEquals("1533673970193", field.getValue())
    }

}