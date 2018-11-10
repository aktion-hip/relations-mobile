package org.elbe.relations.mobile.preferences

import android.content.Context
import android.support.v7.preference.ListPreference
import android.util.AttributeSet
import org.elbe.relations.mobile.search.Languages

/**
 * Custom preference to display the list of languages for the search index.
 *
 * @see res/xml/preferences.xml
 *
 * see http://androidtechnicalblog.blogspot.ch/2014/04/listpreference-how-to-load-data.html
 * see https://developer.android.com/guide/topics/ui/settings
 */
class SearchIndexPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    init {
        setEntries(entries())
        setEntryValues(entryValues())
    }

    private fun entries(): Array<CharSequence> {
        return Array(Languages.values().size) {
            i -> Languages.values()[i].title
        }
    }

    private fun entryValues(): Array<CharSequence> {
        return Array(Languages.values().size) {
            i -> Languages.values()[i].isoLanguage
        }
    }

}