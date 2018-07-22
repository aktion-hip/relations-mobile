package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.MainActivity
import org.elbe.relations.mobile.dbimport.XMLImporter
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.elbe.relations.mobile.util.ProgressDialog
import java.io.File
import java.lang.Void

private const val TAG = "GoogleDriveProvider"

/**
 * Download files (all or increment) from Google Drive.
 *
 * @see https://github.com/gsuitedevs/android-samples/blob/master/drive/demos/app/src/main/java/com/google/android/gms/drive/sample/demo/BaseDemoActivity.java
 * @see https://github.com/gsuitedevs/android-samples/blob/master/drive/demos/app/src/main/java/com/google/android/gms/drive/sample/demo/RetrieveContentsActivity.java
 * @see https://www.raywenderlich.com/192706/integrating-google-drive-in-android
 */
class GoogleDriveProvider(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory): GoogleDrive {
    private var googleDriveService: GoogleDriveService? = null
    private var mDialogProgress: ProgressDialog? = null
    private val mSynchronize = synchronize
    private val mContext = context
    private val mResources = r
    private val mFactory = factory

    override fun prepare(): Boolean {
        googleDriveService?.let {driveService ->
            if (driveService.checkLoginStatus()) {
                return true
            } else {
                driveService.startActivityForResult()
            }
        }
        return false
    }

    private fun preExecute() {
        mDialogProgress = ProgressDialog.newInstance("Download data.")
        mDialogProgress?.let {
            it.isCancelable = false
            it.show(mContext.supportFragmentManager, "fragment_download")
        }
    }

    override fun execute() {
        preExecute()
        if (mSynchronize) {
            return synchronize()
        }
        return download()
    }

    override fun setGoogleDriveService(driveService: GoogleDriveService): GoogleDrive {
        googleDriveService = driveService
        return this
    }

    override fun setActivityResult(requestCode: Int, data: Intent?) {
        googleDriveService?.let {driveService ->
            driveService.setActivityResult(requestCode, data)
        }
    }

    private fun synchronize() {
        mFactory.setOpenMode(false)
        TODO("Process incremental new data.")
    }

    private fun download() {
        mFactory.setOpenMode(true)
        googleDriveService?.let {driveService ->
            driveService.retrieveFile {downloaded ->
                val importer = AsyncImport<Void, Void, Void>(downloaded, mDialogProgress, mContext, mFactory)
                importer.execute() }
        }
    }

//    ---

    // https://guides.codepath.com/android/handling-progressbars
    private class AsyncImport<Params, Progress, Result>(download: File, progress: ProgressDialog?, context: AppCompatActivity, factory: IndexWriterFactory):
            AsyncTask<Void, Int, Boolean>() {
        val mDialogProgress = progress
        val mDownload = download
        val mContext = context
        val mFactory = factory
        private var mDialogProgress2: ProgressDialog? = null

        override fun doInBackground(vararg p0: Void?): Boolean {
            mDialogProgress?.dismiss()
            mDialogProgress2 = ProgressDialog.newInstance("Import data.")
            mDialogProgress2?.let {
                it.isCancelable = false
                it.show(mContext.supportFragmentManager, "fragment_import")
            }
            val importer = XMLImporter(mDownload, { current, max -> publishProgress(current, max)})
            return importer.import(mContext, mFactory)
        }

        override fun onProgressUpdate(vararg values: Int?) {
            val max = values[1] ?: 0
            if (max > 0) {
                if (mDialogProgress2?.isBar() == true) {
                    mDialogProgress2?.increment()
                } else {
                    mDialogProgress2?.switchToBar(max)
                    mDialogProgress2?.setTitle("Import data.")
                }
            }
        }

        override fun onPostExecute(result: Boolean?) {
            if (result == true) {
                mDownload.delete()
                mDialogProgress2?.finish("Successfully synchronized the database.")
            } else {
                mDialogProgress2?.finish("An error occurred during the import.")
            }
            mDialogProgress2?.dismiss()
            refresh()
        }

        private fun refresh() {
            mContext.finish()
            mContext.startActivity(Intent(mContext, MainActivity::class.java))
        }

    }

}