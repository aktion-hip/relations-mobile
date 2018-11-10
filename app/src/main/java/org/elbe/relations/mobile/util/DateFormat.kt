package org.elbe.relations.mobile.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

val DATE_FORMAT : DateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
val TIME_FORMAT : DateFormat =  SimpleDateFormat("HH:mm", Locale.US)

/**
 * Created by lbenno on 02.03.2018.
 */
fun getDate(date: Date): String {
    return DATE_FORMAT.format(date)
}

fun getTime(date: Date): String {
    return  TIME_FORMAT.format(date)
}
