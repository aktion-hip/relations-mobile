package org.elbe.relations.mobile.data

import android.arch.persistence.room.TypeConverter
import java.util.*

/**
 * Converter for items in the Relations database, converts Long to Date.
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return if (value == null) Date(0) else Date(value)
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}