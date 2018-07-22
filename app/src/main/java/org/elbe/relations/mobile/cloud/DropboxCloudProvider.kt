package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.search.IndexWriterFactory
import java.io.File
import java.io.FileOutputStream

private const val DROP_BOX_PATH_ALL = "/synchronization/relations_all.zip"
private const val DROP_BOX_CLIENT_ID = "relations-cloud/1.0"

/**
 * Download files (all or increment) from Dropbox.
 */
class DropboxCloudProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AbstractCloudProvider<Void, Int, AbstractCloudProvider.SyncResult>(synchronize, context, r, factory) {

    override fun doInBackground(vararg param: Void): SyncResult {
        if (getSynchronize()) {
            return synchronize()
        }
        return download()
    }

    /**
     * @return Boolean true in case of successful download and import.
     */
    private fun download(): SyncResult {
        getIndexWriterFactory().setOpenMode(true)
        val token = getToken()
        if (token.isEmpty()) {
            return SyncResult(false, "Configuration Problem: No Dropbox access token configured!")
        }

        val config = DbxRequestConfig(DROP_BOX_CLIENT_ID)
        val client = DbxClientV2(config, token)
        val zipAll = createTempFile("relationsDownload", ".zip")
        downloadFile(client, DROP_BOX_PATH_ALL, zipAll)

        val importer = XMLImporter(zipAll, { current, max -> publishProgress(current, max)})
        if (importer.import(getContext(), getIndexWriterFactory())) {
            zipAll.delete()
        }

        return SyncResult(true, "Successfully synchronized the database.")
    }

    private fun synchronize(): SyncResult {
        getIndexWriterFactory().setOpenMode(false)
        TODO("Process incremental new data.")
    }

    private fun downloadFile(client: DbxClientV2, dropboxPath: String, local: File) {
        FileOutputStream(local).use {output ->
            client.files().downloadBuilder(dropboxPath).download(output)
        }
    }

}