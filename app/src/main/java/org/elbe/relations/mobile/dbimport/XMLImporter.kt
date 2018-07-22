package org.elbe.relations.mobile.dbimport

import android.content.Context
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * The SAX parser class responsible for importing the data in the zipped XML file to the database.
 */
class XMLImporter(file: File, progress: (Int, Int) -> Unit) {
    private val zipFile = ZipFile(file)
    private val mProgress = progress

    /**
     * Executes the import by parsing the specified zip file content.
     */
    fun import(context: Context, factory: IndexWriterFactory): Boolean {
        val parser = SAXParserFactory.newInstance().newSAXParser().xmlReader
        parser.contentHandler = DBImport(context, factory, mProgress)
        getReader().use {r ->
            parser.parse(InputSource(r))
        }
        return true
    }

    private fun getReader(): Reader {
        val entry = zipFile.entries().nextElement()
        return BufferedReader(InputStreamReader(zipFile.getInputStream(entry)))
    }

//    ---

    companion object {
        private val replacerMap = mapOf("&amp;" to "&")

        fun prepareForImport(toProcess: String): String {
            var processed = toProcess
            for ((old, new) in replacerMap) {
                processed = processed.replace(old, new)
            }
            return processed
        }
    }
}