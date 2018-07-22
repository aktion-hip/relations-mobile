package org.elbe.relations.mobile.cloud

import android.content.Intent

/**
 * Interface defining additional functionality for the AbstractCloudProvider variant to access the Google Drive.
 */
interface GoogleDrive {

    /**
     * Sets the GoogleDriveService instance
     */
    fun setGoogleDriveService(driveService: GoogleDriveService): GoogleDrive

    /**
     * Passes the result of the startActivityForResult() for the Google Drive sign in.
     */
    fun setActivityResult(requestCode: Int, data: Intent?)

    fun prepare(): Boolean

    fun execute()
}