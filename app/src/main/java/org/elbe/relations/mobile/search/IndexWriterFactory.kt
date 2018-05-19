package org.elbe.relations.mobile.search

import android.content.res.Resources
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.elbe.relations.mobile.R
import java.io.File

/**
 * Factory to create a Lucene IndexWriter.
 *
 * @param context: AppCompatActivity
 * @param r: Resources
 */
class IndexWriterFactory(context: AppCompatActivity, r: Resources): IndexFactory() {
    val mContext = context
    val mResources = r
    val mPath = File(context.getExternalFilesDir(null), LUCENE_PATH)
    var mOpenMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND

    /**
     * Creates the IndexWriter instance.
     *
     * @return IndexWriter
     */
    fun createIndexWriter(): IndexWriter {
        return IndexWriter(FSDirectory.open(mPath), createConfiguration(mContext, mResources))
    }

    /**
     * Set the mode how to open an existing Lucene index.
     *
     * @param indexNew: Boolean true in case of creating a new index, false in case of reusing the existing index to append new docs
     */
    fun setOpenMode(indexNew: Boolean) {
        mOpenMode = if (indexNew) IndexWriterConfig.OpenMode.CREATE else IndexWriterConfig.OpenMode.CREATE_OR_APPEND
    }

    private fun createConfiguration(context: AppCompatActivity, r: Resources) : IndexWriterConfig {
        val analyzer = getAnalyzer(context, r)
        val config = IndexWriterConfig(LUCENE_VERSION, analyzer)
        config.setOpenMode(mOpenMode)
        return  config
    }

}