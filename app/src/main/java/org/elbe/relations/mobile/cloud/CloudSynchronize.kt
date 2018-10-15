package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Switch
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.preferences.CloudProviders
import org.elbe.relations.mobile.search.IndexWriterFactory

const val SYNC_SWITCH_VALUE_INCR = "syncSwitchValueIncremental"
private const val TAG = "CloudSynchronize"

/**
 * This class is responsible for downloading the XML data from the cloud and replace the content in the DB.
 */
class CloudSynchronize {

    companion object {
        /**
         * Standard synchronize() triggered from the MainActivity's onOptionsItemSelected() method.
         */
        fun synchronize(context: AppCompatActivity, r: Resources, googleDriveService: GoogleDriveService): Boolean {
            val dialog = AlertDialog.Builder(context)
            val inflater = context.layoutInflater
            val view = inflater.inflate(R.layout.dialog_cloud_sync, null)
            setSwitch(view)
            dialog.setView(view)
                    .setTitle(r.getString(R.string.menu_title_sync_db))
                    .setPositiveButton("Ok") { _, _ -> doSync(context, r, googleDriveService, view)}
                    .setNegativeButton("Cancel") { _, _ -> }
            dialog.show()

            return false
        }

        private fun setSwitch(view: View) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
            val isIncremental = preferences.getBoolean(SYNC_SWITCH_VALUE_INCR, true)
            val switch = view.findViewById<Switch>(R.id.switch_cloud_sync)
            switch.isChecked = isIncremental
        }

        private fun doSync(context: AppCompatActivity, r: Resources, googleDriveService: GoogleDriveService, view: View) {
            val providerConfig = getProviderConfig(context, r)
            Log.v(TAG, "doSync: start data synchronization using ${providerConfig.id}.")
            val providerClass = getProviderClass(providerConfig.id, r)
            val provider = createInstance(providerClass, isIncremental(view), context, r, IndexWriterFactory(context, r))
            if (provider is AbstractCloudProvider<*,*,*>) {
                // execute() starts the AsyncTask
                provider.setToken(providerConfig.token).execute()
            } else if (provider is GoogleDrive) {
                if (provider.setGoogleDriveService(googleDriveService).prepare()) {
                    provider.execute()
                }
            }
        }

        private fun isIncremental(view: View): Boolean {
            val switch = view.findViewById<Switch>(R.id.switch_cloud_sync)
            // set switch value to preferences
            val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
            val editor = preferences.edit()
            editor.putBoolean(SYNC_SWITCH_VALUE_INCR, switch.isChecked)
            editor.commit()
            // return value
            return switch.isChecked
        }

        /**
         * Special synchronize() called from the MainActivity's onActivityResult() method.
         */
        fun synchronizeFromGoogleDrive(context: AppCompatActivity, r: Resources, googleDriveService: GoogleDriveService, data: Intent?) {
            Log.v(TAG, "returning from Google Drive sign in [GoogleDriveService.startActivityForResult()].")
            val providerConfig = getProviderConfig(context, r)
            val providerClass = getProviderClass(providerConfig.id, r)
            val provider = createInstance(providerClass, checkIncremental(context), context, r, IndexWriterFactory(context, r))
            if (provider is GoogleDrive) {
                Log.v(TAG, "about to execute with GoogleDrive.")
                if (provider.setGoogleDriveService(googleDriveService).setActivityResult(data)) {
                    // execute() starts the AsyncTask, no prepare needed here
                    provider.execute()
                }
            }
        }

        private fun checkIncremental(context: AppCompatActivity): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return preferences.getBoolean(SYNC_SWITCH_VALUE_INCR, true)
        }

        private fun getProviderConfig(context: AppCompatActivity, r: Resources): ProviderConfig {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val providerId = preferences.getString(r.getString(R.string.key_preference_cloud_config), "")
            return ProviderConfig(providerId, preferences.getString(providerId, ""))
        }

        private fun getProviderClass(providerId: String, r: Resources): String {
            val providers = CloudProviders(r).getProviders()
            for (provider in providers) {
                if (provider.id.equals(providerId)) {
                    return  provider.className
                }
            }
            return ""
        }

        private fun createInstance(className: String, incremental: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory): Any {
            val classObj = Class.forName(className)
            val constructor = classObj.getConstructor(Boolean::class.java, AppCompatActivity::class.java, Resources::class.java, IndexWriterFactory::class.java)
            return constructor.newInstance(incremental, context, r, factory)
        }
    }

//    ---

    private data class ProviderConfig(val id: String, val token: String)

}