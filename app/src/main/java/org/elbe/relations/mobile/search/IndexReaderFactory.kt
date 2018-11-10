package org.elbe.relations.mobile.search

import android.content.Context
import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.store.FSDirectory
import java.io.File

/**
 * Factory to create a Lucene IndexReader.
 */
class IndexReaderFactory(context: AppCompatActivity, r: Resources): IndexFactory() {
    private val mPath = File(context.getExternalFilesDir(null), LUCENE_PATH)
    val mContext = context
    private val mResources = r

    /**
     * Creates the IndexReader instance.
     *
     * @return IndexReader
     */
    fun createIndexReader(): IndexReader {
        return DirectoryReader.open(FSDirectory.open(mPath))
    }

    fun getAnalyzer(): Analyzer {
        return super.getAnalyzer(mContext, mResources)
    }

    companion object {

        /**
         * @return the number of documents in the index
         */
        fun getNumberOfIndexed(context: Context): Int {
            val path = File(context.getExternalFilesDir(null), LUCENE_PATH)
            if (path.exists()) {
                DirectoryReader.open(FSDirectory.open(path)).use {reader ->
                    return reader.numDocs()
                }
            }
            return 0
        }
    }

}