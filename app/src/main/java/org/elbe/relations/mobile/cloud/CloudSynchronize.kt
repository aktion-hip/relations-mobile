package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.preferences.CloudProviders
import org.elbe.relations.mobile.search.IndexWriterFactory

/**
 * This class is responsible for downloading the XML data from the cloud and replace the content in the DB.
 */
class CloudSynchronize {

    companion object {
        /**
         * Standard synchronize() triggered from the MainActivity's onOptionsItemSelected() method.
         */
        fun synchronize(context: AppCompatActivity, r: Resources, googleDriveService: GoogleDriveService): Boolean {
            val providerConfig = getProviderConfig(context, r)
            val providerClass = getProviderClass(providerConfig.id, r)
            val provider = createInstance(providerClass, false, context, r, IndexWriterFactory(context, r))
            if (provider is AbstractCloudProvider<*,*,*>) {
                // execute() starts the AsyncTask
                provider.setToken(providerConfig.token).execute()
                return true
            } else if (provider is GoogleDrive) {
                if (provider.setGoogleDriveService(googleDriveService).prepare()) {
                    provider.execute()
                }
                return true
            }
            return false
        }

        /**
         * Special synchronize() called from the MainActivity's onActivityResult() method.
         */
        fun synchronizeFromGoogleDrive(context: AppCompatActivity, r: Resources, googleDriveService: GoogleDriveService, requestCode: Int, data: Intent?) {
            val providerConfig = getProviderConfig(context, r)
            val providerClass = getProviderClass(providerConfig.id, r)
            val provider = createInstance(providerClass, false, context, r, IndexWriterFactory(context, r))
            if (provider is AbstractCloudProvider<*,*,*>) {
                if (provider is GoogleDrive) {
                    provider.setGoogleDriveService(googleDriveService).setActivityResult(requestCode, data)
                }
                provider.execute()
            }
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

        private fun createInstance(className: String, synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory): Any {
            val classObj = Class.forName(className)
            val constructor = classObj.getConstructor(Boolean::class.java, AppCompatActivity::class.java, Resources::class.java, IndexWriterFactory::class.java)
            return constructor.newInstance(synchronize, context, r, factory)
        }
    }

//    ---

    private data class ProviderConfig(val id: String, val token: String)

}