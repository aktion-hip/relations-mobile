package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveClient
import com.google.android.gms.drive.DriveFile
import com.google.android.gms.drive.DriveResourceClient
import com.google.android.gms.drive.query.*
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.util.ProgressDialog
import java.io.File

class GoogleDriveService(private val context: AppCompatActivity) {
    private val mContext = context

    companion object {
        private val SCOPES = setOf<Scope>(Drive.SCOPE_FILE, Drive.SCOPE_APPFOLDER)

        private const val DRIVE_NAME = "relations_all.zip"
        private const val DRIVE_NAME_INCR = "relations_delta_"
        private const val DRIVE_PATH = "relations"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val MIME_TYPE_FILE = "application/zip"
        val documentMimeTypes = arrayListOf(MIME_TYPE_FOLDER, MIME_TYPE_FILE)

        const val REQUEST_CODE_OPEN_ITEM = 100
        const val REQUEST_CODE_SIGN_IN = 101
        const val TAG = "GoogleDriveService"
    }

    private var signInAccount: GoogleSignInAccount? = null
    private var driveClient: DriveClient? = null
    private var driveResourceClient: DriveResourceClient? = null

    private val googleSignInClient: GoogleSignInClient by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        for (scope in SCOPES) {
            builder.requestScopes(scope)
        }
        val signInOptions = builder.build()
        GoogleSignIn.getClient(mContext, signInOptions)
    }

    /**
     * Initialize signInAccount if user has signed in and no new scope
     */
    fun checkLoginStatus(): Boolean {
        val requiredScopes = HashSet<Scope>(2)
        requiredScopes.add(Drive.SCOPE_FILE)
        requiredScopes.add(Drive.SCOPE_APPFOLDER)
        signInAccount = GoogleSignIn.getLastSignedInAccount(context)
        val containsScope = signInAccount?.grantedScopes?.containsAll(requiredScopes)
        if (signInAccount != null && containsScope == true) {
            initializeDriveClient(signInAccount!!)
            return true
        }
        return false
    }

    /**
     * Continues the sign-in process, initializing the Drive clients with the current
     * user's account.
     */
    private fun initializeDriveClient(signInAccount: GoogleSignInAccount) {
        driveClient = Drive.getDriveClient(context.applicationContext, signInAccount)
        driveResourceClient = Drive.getDriveResourceClient(context.applicationContext, signInAccount)
        //serviceListener?.loggedIn()
    }

    /**
     * Triggers the startActivityForResult() for the Google Drive sign in.
     */
    fun startActivityForResult() {
        context.startActivityForResult(googleSignInClient.signInIntent, REQUEST_CODE_SIGN_IN)
    }

    fun setActivityResult(requestCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SIGN_IN) {
            if (data != null) {
                handleSignIn(data)
            }
        }
    }

    fun logout() {
        googleSignInClient.signOut()
    }

    /**
     * Handle the activity result when signing in
     */
    private fun handleSignIn(data: Intent) {
        val getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data)
        if (getAccountTask.isSuccessful) {
            initializeDriveClient(getAccountTask.result)
        } else {
            Log.d(TAG, getAccountTask.exception?.message)
        }
    }

    // ---

    // https://developers.google.com/drive/android/folders
    // https://developers.google.com/drive/android/files
    fun retrieveFile(process: (File) -> Unit) {
        driveResourceClient?.let {drive ->
            val rootFolderTask = drive.rootFolder
            rootFolderTask.continueWithTask {task ->
                val parent = task.result
                val query = Query.Builder()
                        .addFilter(Filters.and(
                                Filters.eq(SearchableField.TITLE, DRIVE_PATH),
                                Filters.eq(SearchableField.MIME_TYPE, MIME_TYPE_FOLDER)))
                        .build()
                return@continueWithTask drive.queryChildren(parent, query)
            }.addOnSuccessListener {meta ->
                if (meta.count == 0) {
                    Log.d(TAG, "No folder '$DRIVE_PATH' found!")
                } else {
                    Log.d(TAG, "Looking up content of folder '${meta.get(0).title}'!")
                }
            }.continueWithTask {task ->
                val meta = task.result
                val parent = meta.get(0).driveId.asDriveFolder()
                val query = Query.Builder().addFilter(Filters.and(
                        Filters.eq(SearchableField.TITLE, DRIVE_NAME),  // equals relations_all.zip
                        Filters.eq(SearchableField.MIME_TYPE, MIME_TYPE_FILE)
                )).build()
                return@continueWithTask drive.queryChildren(parent, query)
            }.addOnSuccessListener {meta ->
                if (meta.count == 0) {
                    Log.d(TAG, "No file '$DRIVE_NAME' found!")
                } else {
                    Log.d(TAG, "Starting download of file '${meta.get(0).title}'!")
                }
            }.continueWith {task ->
                val meta = task.result
                meta.forEach { metadata ->
                    if (!metadata.isTrashed) {
                        // if cloud file is not deleted: download and process it
                        task.continueWithTask {
                            drive.openFile(metadata.driveId.asDriveFile(), DriveFile.MODE_READ_ONLY)
                        }.continueWithTask { task ->
                            val cloudFile = task.result
                            cloudFile.inputStream.use { zipCloud ->
                                val zipLocal = createTempFile("relationsDownload", ".zip")
                                if (zipLocal.exists()) {
                                    zipLocal.delete()
                                }
                                zipLocal.outputStream().use { zipOut ->
                                    zipCloud.copyTo(zipOut)
                                }
                                process(zipLocal)
                            }
                            return@continueWithTask drive.discardContents(cloudFile)
                        }?.addOnFailureListener {e ->
                            Log.e(TAG, "Unable to read the contents of the downloaded file!", e)
                        }
                    }
                }
                return@continueWith
            }
        }
    }

    fun retrieveIncrements(progress: ProgressDialog?, r: Resources, process: (File) -> Unit) {
        driveResourceClient?.let {drive ->
            val rootFolderTask = drive.rootFolder
            rootFolderTask.continueWithTask {task ->
                val parent = task.result
                val query = Query.Builder()
                        .addFilter(Filters.and(
                                Filters.eq(SearchableField.TITLE, DRIVE_PATH),
                                Filters.eq(SearchableField.MIME_TYPE, MIME_TYPE_FOLDER)))
                        .build()
                return@continueWithTask drive.queryChildren(parent, query)
            }.addOnSuccessListener {meta ->
                if (meta.count == 0) {
                    Log.d(TAG, "No folder '$DRIVE_PATH' found!")
                } else {
                    Log.d(TAG, "Looking up content of folder '${meta.get(0).title}'!")
                }
            }.continueWithTask {task ->
                val meta = task.result
                val parent = meta.get(0).driveId.asDriveFolder()
                val sortOrder = SortOrder.Builder().addSortAscending(SortableField.CREATED_DATE).build()
                val query = Query.Builder().addFilter(Filters.and(
                        Filters.contains(SearchableField.TITLE, DRIVE_NAME_INCR), // starts with relations_delta_
                        Filters.eq(SearchableField.MIME_TYPE, MIME_TYPE_FILE)))
                .setSortOrder(sortOrder)
                .build()
                return@continueWithTask drive.queryChildren(parent, query)
            }.addOnSuccessListener {meta ->
                if (meta.count == 0) {
                    // finish progress dialog with message 'no incremental'
                    AbstractCloudProvider.switchIncrementalVal(mContext)
                    progress?.let {progress ->
                        progress.finish(r.getString(R.string.abstract_cloud_provider_no_incremental))
                        progress.dismiss()
                    }
                } else {
                    Log.d(TAG, "Starting download of file '${meta.get(0).title}'!")
                }
            }.continueWith {task ->
                val meta = task.result
                meta.forEach {metadata ->
                    if (!metadata.isTrashed) {
                        downloadIncrement(metadata.driveId.asDriveFile(), drive, process)
                    }
                }
                return@continueWith
            }?.addOnFailureListener {e ->
                Log.e(TAG, "Unable to read the contents of the downloaded file!", e)
            }
        }
    }

    private fun downloadIncrement(file: DriveFile, drive: DriveResourceClient, process: (File) -> Unit) {
        drive.openFile(file, DriveFile.MODE_READ_ONLY).continueWithTask {task ->
            val cloudFile = task.result
            cloudFile.inputStream.use { zipCloud ->
                val zipLocal = createTempFile("relationsDownload", ".zip")
                if (zipLocal.exists()) {
                    zipLocal.delete()
                }
                zipLocal.outputStream().use { zipLocal ->
                    zipCloud.copyTo(zipLocal)
                }
                process(zipLocal)
            }
            return@continueWithTask drive.discardContents(cloudFile)
        }
    }

}