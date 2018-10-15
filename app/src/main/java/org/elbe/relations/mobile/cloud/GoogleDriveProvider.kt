package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.util.Log
import org.elbe.relations.mobile.MainActivity
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.dbimport.AbstractDBImport
import org.elbe.relations.mobile.dbimport.DBImportFull
import org.elbe.relations.mobile.dbimport.DBImportIncremental
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
class GoogleDriveProvider(incremental: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory): GoogleDrive {
    private var mGoogleDriveService: GoogleDriveService? = null
    private var mDialogProgress: ProgressDialog? = null
    private val mIncremental = incremental
    private val mContext = context
    private val mResources = r
    private val mFactory = factory

    override fun prepare(): Boolean {
        mGoogleDriveService?.let { driveService ->
            if (driveService.checkLoginStatus()) {
                Log.v(TAG, "prepare: user is logged in Google Drive.")
                return true
            } else {
                Log.v(TAG, "prepare: user has to log into Google Drive -> about to sign in!")
                driveService.startActivityForResult()
            }
        }
        return false
    }

    private fun preExecute() {
        mDialogProgress = ProgressDialog.newInstance(mResources.getString(R.string.abstract_cloud_provider_dialog_title1))
        mDialogProgress?.let {
            Log.v(TAG, "preExecute: about to show download dialog.")
            it.isCancelable = false
            it.show(mContext.supportFragmentManager, "fragment_download")
        }
    }

    override fun execute() {
        preExecute()
        if (mIncremental) {
            return incrementalSync()
        }
        return fullSync()
    }

    override fun setGoogleDriveService(driveService: GoogleDriveService): GoogleDrive {
        mGoogleDriveService = driveService
        return this
    }

    override fun setActivityResult(data: Intent?): Boolean {
        mGoogleDriveService?.let { driveService ->
            return driveService.setActivityResult(data)
        }
        return false
    }

    private fun incrementalSync() {
        Log.v(TAG, "Starting incremental sync.")
        mFactory.setOpenMode(false)
        mGoogleDriveService?.let { driveService ->
            driveService.retrieveIncrements(mDialogProgress, mResources) {downloaded ->
                val importer = AsyncImport<Void, Void, Void>(downloaded, mDialogProgress, mContext, DBImportIncremental(mContext, mFactory), mResources)
                importer.execute()
            }
        }
    }

    private fun fullSync() {
        Log.v(TAG, "Starting full sync.")
        mFactory.setOpenMode(true)
        mGoogleDriveService?.let { driveService ->
            driveService.retrieveFile {downloaded ->
                val importer = AsyncImport<Void, Void, Void>(downloaded, mDialogProgress, mContext, DBImportFull(mContext, mFactory), mResources)
                importer.execute()
            }
        }
    }

//    ---

    // https://guides.codepath.com/android/handling-progressbars
    private class AsyncImport<Params, Progress, Result>(download: File, progress: ProgressDialog?, context: AppCompatActivity, handler: AbstractDBImport, r: Resources):
            AsyncTask<Void, Int, Boolean>() {
        private val mDialogProgress = progress
        private val mDownload = download
        private val mContext = context
        private val mHandler = handler
        private val mResources = r
        private var mDialogProgress2: ProgressDialog? = null

        override fun doInBackground(vararg p0: Void?): Boolean {
            mDialogProgress?.dismiss()
            mDialogProgress2 = ProgressDialog.newInstance(mResources.getString(R.string.abstract_cloud_provider_dialog_title2))
            mDialogProgress2?.let {progress ->
                progress.isCancelable = false
                progress.show(mContext.supportFragmentManager, "fragment_import")
            }
            val importer = XMLImporter(mDownload)
            mHandler.setProgress { current, max ->
                publishProgress(current, max)
            }
            return importer.import(mHandler)
        }

        override fun onProgressUpdate(vararg values: Int?) {
            val max = values[1] ?: 0
            if (max > 0) {
                if (mDialogProgress2?.isBar() == true) {
                    mDialogProgress2?.increment()
                } else {
                    mDialogProgress2?.switchToBar(max)
                    mDialogProgress2?.setTitle(mResources.getString(R.string.abstract_cloud_provider_dialog_title2))
                }
            }
        }

        override fun onPostExecute(result: Boolean?) {
            if (result == true) {
                mDownload.delete()
                mDialogProgress2?.finish(mResources.getString(R.string.cloud_provider_dft_success))
            } else {
                mDialogProgress2?.finish(mResources.getString(R.string.cloud_provider_drive_error))
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