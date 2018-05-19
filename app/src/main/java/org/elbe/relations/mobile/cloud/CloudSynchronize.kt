package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.search.IndexWriterFactory

/**
 * This class is responsible for downloading the XML data from the cloud and replace the content in the DB.
 */
class CloudSynchronize {

    companion object {
        fun synchronize(context: AppCompatActivity, r: Resources): Boolean {
            val indexWriterFactory = IndexWriterFactory(context, r)
            DropboxCloudProvider(false, context, r, indexWriterFactory).execute()
            return true
        }
    }

}