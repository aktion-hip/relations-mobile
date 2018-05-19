package org.elbe.relations.mobile.search

import org.apache.lucene.index.IndexWriter

/**
 * Interface for all models that are indexable, i.e. that have content that should be included into full text search index.
 */
interface Indexable {

    /**
     * Create an entry for the Lucene search index.
     *
     * @param writer: IndexWriter
     */
    fun indexContent(writer: IndexWriter)
}