package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.file.CloudFile
import com.microsoft.azure.storage.file.CloudFileDirectory
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.dbimport.DBImportFull
import org.elbe.relations.mobile.dbimport.DBImportIncremental
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.search.IndexWriterFactory

/**
 * Download files (all or increment) from MS Azure.
 */
private const val AZ_SHARE = "relations"
private const val AZ_FILE_NAME_ALL = "relations_all.zip"
private const val AZ_FILE_NAME_INCR = "relations_delta_"

class MSAzureCloudProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AbstractCloudProvider<Void, Int, AbstractCloudProvider.SyncResult>(synchronize, context, r, factory) {

    override fun doInBackground(vararg param: Void): SyncResult {
        if (isIncremental()) {
            if (!hasIncremental()) {
                return sendNoIncremental()
            }
            return incrementalSync()
        }
        return fullSync()
    }

    private fun hasIncremental(): Boolean {
        val storageAccount = CloudStorageAccount.parse(getToken())
        val fileClient = storageAccount.createCloudFileClient()
        val share = fileClient.getShareReference(AZ_SHARE)
        if (!share.exists()) {
            return false
        }
        val rootDir = share.rootDirectoryReference
        val incremental = rootDir.listFilesAndDirectories(AZ_FILE_NAME_INCR, null, null)
        return incremental.count() > 0
    }

    private fun incrementalSync(): SyncResult {
        val value = getRootDir()
        value.second?.let {syncResult ->
            return syncResult
        }

        getIndexWriterFactory().setOpenMode(false)
        val incrementalFiles = value.first!!.listFilesAndDirectories(AZ_FILE_NAME_INCR, null, null)
        // create a list of files
        val fileList = mutableListOf<CloudFile>()
        incrementalFiles.forEach { incremental ->
            if (incremental is CloudFile) {
                fileList.add(incremental)
            }
        }
        // sort by date
        val sortedList = fileList.sortedWith(compareBy { it.properties.lastModified } )
        // process the list items
        sortedList.forEach { incremental ->
            val zipIncremental = createTempFile("relationsDownload", ".zip")
            incremental.downloadToFile(zipIncremental.absolutePath)

            val importer = XMLImporter(zipIncremental)
            val handler = DBImportIncremental(getContext(), getIndexWriterFactory()).setProgress { current, max ->
                publishProgress(current, max)
            }
            if (importer.import(handler)) {
                zipIncremental.delete()
                // delete the increments in the cloud
                incremental.delete()
            }
        }

        return SyncResult(true, getResources().getString(R.string.cloud_provider_dft_success))
    }

    private fun getRootDir(): Pair<CloudFileDirectory?, SyncResult?> {
        val connectString = getToken()
        if (connectString.isEmpty()) {
            return Pair(null, SyncResult(false, getResources().getString(R.string.cloud_provider_azure_no_config)))
        }

        val storageAccount = CloudStorageAccount.parse(connectString)
        val fileClient = storageAccount.createCloudFileClient()
        val share = fileClient.getShareReference(AZ_SHARE)
        if (!share.exists()) {
            return Pair(null, SyncResult(false, String.format(getResources().getString(R.string.cloud_provider_azure_no_export1), AZ_SHARE)))
        }
        return Pair(share.rootDirectoryReference, null)
    }

    private fun fullSync(): SyncResult {
        val value = getRootDir()
        value.second?.let {syncResult ->
            return syncResult
        }

        getIndexWriterFactory().setOpenMode(true)
        val cloudFile = value.first!!.getFileReference(AZ_FILE_NAME_ALL)
        if (!cloudFile.exists()) {
            return SyncResult(false, String.format(getResources().getString(R.string.cloud_provider_azure_no_export2), AZ_SHARE, AZ_FILE_NAME_ALL))
        }
        val zipAll = createTempFile("relationsDownload", ".zip")
        cloudFile.downloadToFile(zipAll.absolutePath)

        val importer = XMLImporter(zipAll)
        val handler = DBImportFull(getContext(), getIndexWriterFactory()).setProgress { current, max ->
            publishProgress(current, max)
        }
        if (importer.import(handler)) {
            zipAll.delete()
        }
        return SyncResult(true, getResources().getString(R.string.cloud_provider_dft_success))
    }

}