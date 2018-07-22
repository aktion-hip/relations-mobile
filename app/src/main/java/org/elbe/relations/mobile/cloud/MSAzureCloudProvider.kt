package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import com.microsoft.azure.storage.CloudStorageAccount
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.search.IndexWriterFactory

/**
 * Download files (all or increment) from MS Azure.
 */
class MSAzureCloudProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AbstractCloudProvider<Void, Int, AbstractCloudProvider.SyncResult>(synchronize, context, r, factory) {
    private val AZ_SHARE = "relations"
    private val AZ_FILE_NAME = "relations_all.zip"

    override fun doInBackground(vararg param: Void): SyncResult {
        if (getSynchronize()) {
            return synchronize()
        }
        return download()
    }

    private fun synchronize(): SyncResult {
        getIndexWriterFactory().setOpenMode(false)
        TODO("Process incremental new data.")
    }

    private fun download(): SyncResult {
        val connectString = getToken()
        if (connectString.isEmpty()) {
            return SyncResult(false, "Configuration Problem: No MS Azure connection string is configured!")
        }

        getIndexWriterFactory().setOpenMode(true)
        val storageAccount = CloudStorageAccount.parse(connectString)
        val fileClient = storageAccount.createCloudFileClient()
        val share = fileClient.getShareReference(AZ_SHARE)
        if (!share.exists()) {
            return SyncResult(false, "No Relations export found on <MS Azure>:/${AZ_SHARE}!")
        }
        val rootDir = share.getRootDirectoryReference()
        val cloudFile = rootDir.getFileReference(AZ_FILE_NAME)
        if (!cloudFile.exists()) {
            return SyncResult(false, "No Relations export <MS Azure>:/${AZ_SHARE}/${AZ_FILE_NAME} found!")
        }
        val zipAll = createTempFile("relationsDownload", ".zip")
        cloudFile.downloadToFile(zipAll.absolutePath)

        val importer = XMLImporter(zipAll, { current, max -> publishProgress(current, max)})
        if (importer.import(getContext(), getIndexWriterFactory())) {
            zipAll.delete()
        }

        return SyncResult(true, "Successfully synchronized the database.")
    }

}