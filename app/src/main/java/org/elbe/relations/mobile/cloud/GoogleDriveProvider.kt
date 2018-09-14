package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
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

/**
 * Download files (all or increment) from Google Drive.
 *
 * @see https://github.com/gsuitedevs/android-samples/blob/master/drive/demos/app/src/main/java/com/google/android/gms/drive/sample/demo/BaseDemoActivity.java
 * @see https://github.com/gsuitedevs/android-samples/blob/master/drive/demos/app/src/main/java/com/google/android/gms/drive/sample/demo/RetrieveContentsActivity.java
 * @see https://www.raywenderlich.com/192706/integrating-google-drive-in-android
 */
class GoogleDriveProvider(incremental: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory): GoogleDrive {
    private var googleDriveService: GoogleDriveService? = null
    private var mDialogProgress: ProgressDialog? = null
    private val mIncremental = incremental
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
        mDialogProgress = ProgressDialog.newInstance(mResources.getString(R.string.abstract_cloud_provider_dialog_title1))
        mDialogProgress?.let {
            it.isCancelable = false
            it.show(mContext.supportFragmentManager, "fragment_download")
        }
    }

    override fun execute() {
        preExecute()
        if (mIncremental) {
            // !hasIncremental
            if (false) {
                AbstractCloudProvider.switchIncrementalVal(mContext)
                mDialogProgress?.finish(mResources.getString(R.string.abstract_cloud_provider_no_incremental))
                return
            }
            return incrementalSync()
        }
        return fullSync()
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

    private fun incrementalSync() {
        mFactory.setOpenMode(false)
        googleDriveService?.let {driveService ->
            driveService.retrieveIncrements(mDialogProgress, mResources) {downloaded ->
                val importer = AsyncImport<Void, Void, Void>(downloaded, mDialogProgress, mContext, DBImportIncremental(mContext, mFactory), mResources)
                importer.execute()
            }
        }
    }

    private fun fullSync() {
        mFactory.setOpenMode(true)
        googleDriveService?.let {driveService ->
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