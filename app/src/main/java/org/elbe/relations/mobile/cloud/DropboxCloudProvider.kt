package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.dbimport.DBImportFull
import org.elbe.relations.mobile.dbimport.DBImportIncremental
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.search.IndexWriterFactory
import java.io.File
import java.io.FileOutputStream

private const val DROP_BOX_PATH_BASE = "/synchronization"
private const val DROP_BOX_PATH_ALL = "$DROP_BOX_PATH_BASE/relations_all.zip"
private const val DROP_BOX_CLIENT_ID = "relations-cloud/1.0"
private const val FILE_PREFIX_INCR = "relations_delta_"

/**
 * Download files (all or increment) from Dropbox.
 */
class DropboxCloudProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
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
        val value = getRootDir()
        value.first?.let {client ->
            var relationsContent = client.files().listFolder(DROP_BOX_PATH_BASE)
            while (true) {
                relationsContent.entries.forEach {metadata ->
                    if (metadata.name.startsWith(FILE_PREFIX_INCR)) {
                        return true
                    }
                }
                if (!relationsContent.hasMore) {
                    break;
                }
                relationsContent = client.files().listFolderContinue(relationsContent.cursor)
            }
        }
        return false
    }

    private fun getRootDir(): Pair<DbxClientV2?, SyncResult?> {
        val token = getToken()
        if (token.isEmpty()) {
            return Pair(null, SyncResult(false, getResources().getString(R.string.cloud_provider_dropbox_no_config)))
        }

        val config = DbxRequestConfig(DROP_BOX_CLIENT_ID)
        return Pair(DbxClientV2(config, token), null)
    }

    /**
     * @return Boolean true in case of successful sync and import.
     */
    private fun fullSync(): SyncResult {
        val value = getRootDir()
        value.second?.let {syncResult ->
            return syncResult
        }

        getIndexWriterFactory().setOpenMode(true)
        val zipAll = createTempFile("relationsDownload", ".zip")
        downloadFile(value.first!!, DROP_BOX_PATH_ALL, zipAll)

        val importer = XMLImporter(zipAll)
        val handler = DBImportFull(getContext(), getIndexWriterFactory()).setProgress { current, max ->
            publishProgress(current, max)
        }
        if (importer.import(handler)) {
            zipAll.delete()
        }

        return SyncResult(true, getResources().getString(R.string.cloud_provider_dft_success))
    }

    private fun incrementalSync(): SyncResult {
        val value = getRootDir()
        value.second?.let {syncResult ->
            return syncResult
        }

        getIndexWriterFactory().setOpenMode(false)
        value.first?.let { client ->
            // create a list of files
            var relationsContent = client.files().listFolder(DROP_BOX_PATH_BASE)
            val fileList = mutableListOf<FileMetadata>()
            while (true) {
                relationsContent.entries.forEach {metadata ->
                    if (metadata is FileMetadata) {
                        if (metadata.name.startsWith(FILE_PREFIX_INCR)) {
                           fileList.add(metadata)
                        }
                    }
                }
                if (!relationsContent.hasMore) {
                    break;
                }
                relationsContent = client.files().listFolderContinue(relationsContent.cursor)
            }
            // sort by date
            val sortedList = fileList.sortedWith(compareBy { it.clientModified })
            // process the list items
            sortedList.forEach {metadata ->
                processIncremental(metadata, client)
            }
        }

        return SyncResult(true, getResources().getString(R.string.cloud_provider_dft_success))
    }

    private fun processIncremental(metadata: FileMetadata, client: DbxClientV2) {
        val zipIncremental = createTempFile(metadata.name, ".zip")  // creates the temporary file
        downloadFile(client, "$DROP_BOX_PATH_BASE/${metadata.name}", zipIncremental)

        val importer = XMLImporter(zipIncremental)
        val handler = DBImportIncremental(getContext(), getIndexWriterFactory()).setProgress { current, max ->
            publishProgress(current, max)
        }
        if (importer.import(handler)) {
            zipIncremental.delete()  // deletes the temporary file
            // delete the increments in the cloud
            client.files().deleteV2(metadata.pathDisplay)
        }
    }

    private fun downloadFile(client: DbxClientV2, dropboxPath: String, local: File) {
        FileOutputStream(local).use {output ->
            client.files().downloadBuilder(dropboxPath).download(output)
        }
    }

}