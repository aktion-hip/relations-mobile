package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.search.IndexWriterFactory

/**
 * Download files (all or increment) from MS Azure.
 */
class MSAzureCloudProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AbstractCloudProvider<Void, Int, DropboxCloudProvider.SyncResult>(synchronize, context, r, factory) {

    override fun doInBackground(vararg p0: Void?): DropboxCloudProvider.SyncResult {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}