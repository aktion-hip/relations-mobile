package org.elbe.relations.mobile.search

import android.content.res.Resources
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.util.Version
import org.elbe.relations.mobile.R

val LUCENE_VERSION = Version.LUCENE_41
val LUCENE_PATH = "lucene"
val MAX_HITS = 100
val INDX_CONTENT_FULL = "itemFull"
val INDX_TITLE = "itemTitle"
val INDX_UNIQUE_ID = "uniqueID"

/**
 * Base class for Lucene IndexReader- and IndexWriterFactory, providing the functionality to get the correct analyzer instance.
 */
abstract class IndexFactory {

    fun getAnalyzer(context: AppCompatActivity, r: Resources): Analyzer {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return Languages.getAnalyzer(preferences.getString(r.getString(R.string.key_preference_index_language), "en"))
    }

}