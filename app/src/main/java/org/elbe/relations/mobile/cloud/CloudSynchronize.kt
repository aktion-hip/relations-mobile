package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Switch
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.preferences.SettingsActivity
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
        fun synchronize(context: AppCompatActivity, r: Resources): Boolean {
            if (CloudProviderKind.fromId(getProviderConfig(context, r).id) == null) {
                showUnsupportedProvider(context, r)
                return false
            }
            val dialog = AlertDialog.Builder(context)
            val inflater = context.layoutInflater
            val view = inflater.inflate(R.layout.dialog_cloud_sync, null)
            setSwitch(view)
            dialog.setView(view)
                    .setTitle(r.getString(R.string.menu_title_sync_db))
                    .setPositiveButton("Ok") { _, _ -> doSync(context, r, view)}
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

        /**
         * Tells the user that the stored cloud provider (e.g. Google Drive) is no longer supported
         * and offers to open the settings to select a supported one.
         */
        private fun showUnsupportedProvider(context: AppCompatActivity, r: Resources) {
            AlertDialog.Builder(context)
                    .setTitle(r.getString(R.string.menu_title_sync_db))
                    .setMessage(r.getString(R.string.cloud_provider_unsupported))
                    .setPositiveButton(r.getString(R.string.action_settings)) { _, _ ->
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
        }

        private fun doSync(context: AppCompatActivity, r: Resources, view: View) {
            val providerConfig = getProviderConfig(context, r)
            Log.v(TAG, "doSync: start data synchronization using ${providerConfig.id}.")
            val kind = CloudProviderKind.fromId(providerConfig.id)
            if (kind == null) {
                showUnsupportedProvider(context, r)
                return
            }
            // the provider must not hold a reference to the activity, it outlives it (e.g. device rotation)
            val appContext = context.applicationContext
            val factory = IndexWriterFactory(appContext, r)
            val provider = when (kind) {
                CloudProviderKind.DROPBOX -> DropboxCloudProvider(appContext, r, factory, providerConfig.token)
                CloudProviderKind.MS_AZURE -> MSAzureCloudProvider(appContext, r, factory, providerConfig.token)
            }
            SyncRunner.instance.start(provider, isIncremental(view), r.getString(R.string.abstract_cloud_provider_dft_error))
        }

        private fun isIncremental(view: View): Boolean {
            val switch = view.findViewById<Switch>(R.id.switch_cloud_sync)
            // set switch value to preferences
            val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
            val editor = preferences.edit()
            editor.putBoolean(SYNC_SWITCH_VALUE_INCR, switch.isChecked)
            editor.apply()
            // return value
            return switch.isChecked
        }

        private fun getProviderConfig(context: AppCompatActivity, r: Resources): ProviderConfig {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val providerId = preferences.getString(r.getString(R.string.key_preference_cloud_config), "") ?: ""
            return ProviderConfig(providerId, preferences.getString(providerId, "") ?: "")
        }
    }

//    ---

    private data class ProviderConfig(val id: String, val token: String)

}