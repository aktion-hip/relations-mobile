package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.elbe.relations.mobile.util.ProgressDialog
import java.io.File
import java.io.FileOutputStream

private const val DROP_BOX_PATH_ALL = "/synchronization/relations_all.zip"
private const val DROP_BOX_CLIENT_ID = "relations-cloud/1.0"

/**
 * Download files (all or increment) from Dropbox.
 */
class DropboxCloudProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory): AsyncTask<Void, Int, DropboxCloudProvider.SyncResult>() {
    private val mSynchronize = synchronize
    private val mContext = context
    private val mResources = r
    private var mDialogProgress: ProgressDialog? = null
    private val mIndexWriterFactory = factory

    override fun onPreExecute() {
        mDialogProgress = ProgressDialog.newInstance("Download data.")
        mDialogProgress?.let {
            it.isCancelable = false
            it.show(mContext.supportFragmentManager, "fragment_download")
        }
    }

    override fun doInBackground(vararg param: Void): SyncResult {
        if (mSynchronize) {
            return synchronize()
        }
        return download()
    }

    override fun onProgressUpdate(vararg values: Int?) {
        val max = values[1] ?: 0
        if (max > 0) {
            if (mDialogProgress?.isBar() ?: false) {
                mDialogProgress?.increment()
            } else {
                mDialogProgress?.switchToBar(max)
                mDialogProgress?.setTitle("Import data.")
            }
        }
    }

    override fun onPostExecute(result: SyncResult?) {
        mDialogProgress?.finish(result?.message ?: "An error occurred during the import.")
        mDialogProgress?.dismiss()
    }

    /**
     * @return Boolean true in case of successful download and import.
     */
    private fun download(): SyncResult {
        mIndexWriterFactory.setOpenMode(true)
        val token = getToken(this.mContext, this.mResources)
        if (token.isEmpty()) {
            return SyncResult(false, "Configuration Problem: No Dropbox access token configured!")
        }

        val config = DbxRequestConfig(DROP_BOX_CLIENT_ID)
        val client = DbxClientV2(config, token)
        val zipAll = createTempFile("relationsDownload", ".zip")
        downloadFile(client, DROP_BOX_PATH_ALL, zipAll)

        val importer = XMLImporter(zipAll, { current, max -> publishProgress(current, max)})
        if (importer.import(mContext, mIndexWriterFactory)) {
            zipAll.delete()
        }

        return SyncResult(true, "Successfully synchronized the database.")
    }

    private fun synchronize(): SyncResult {
        mIndexWriterFactory.setOpenMode(false)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun downloadFile(client: DbxClientV2, dropboxPath: String, local: File) {
        FileOutputStream(local).use {output ->
            client.files().downloadBuilder(dropboxPath).download(output)
        }
    }

    private fun getToken(context: AppCompatActivity, r: Resources): String {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.getString(r.getString(R.string.key_preference_dropbox_token), "")
    }

//    ---

    class SyncResult(val value: Boolean, val message: String) {
        fun getResult(): Boolean {
            return value
        }
    }

}