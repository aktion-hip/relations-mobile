package org.elbe.relations.mobile.search

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
    val mPath = File(context.getExternalFilesDir(null), LUCENE_PATH)
    val mContext = context
    val mResources = r

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
}