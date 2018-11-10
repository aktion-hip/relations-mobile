package org.elbe.relations.mobile.util

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import org.elbe.relations.mobile.IntroActivity
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.cloud.CloudSynchronize
import org.elbe.relations.mobile.cloud.GoogleDriveService
import org.elbe.relations.mobile.preferences.SettingsActivity

/**
 * Helper class providing static methods.
 */
class Utils {

    companion object {

        /**
         * Static method to process the option items (i.e. activity.onOptionsItemSelected(item)).
         *
         * @param item the menu item clicked
         * @param context the activity
         * @param driveService GoogleDriveService instance, used for the syncronize item
         * @param elseOption lamda {item ->  super.onOptionsItemSelected(item)}
         * @return Boolean
         */
        fun runOptions(item: MenuItem?, context: AppCompatActivity, driveService: GoogleDriveService, elseOption: (item: MenuItem?) -> Boolean): Boolean {
            return when (item?.itemId) {
                R.id.action_synchronize -> CloudSynchronize.synchronize(context, context.resources, driveService)
                R.id.action_settings -> {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                    return true
                }
                R.id.action_help -> {
                    context.startActivity(Intent(context, IntroActivity::class.java))
                    return true
                }
                else -> elseOption.invoke(item)
            }
        }
    }

}