package org.elbe.relations.mobile.cloud

import android.content.Context
import android.content.res.Resources
import androidx.preference.PreferenceManager
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.search.IndexWriterFactory

/**
 * A cloud provider downloads the data and imports it into the database and the search index.
 */
interface CloudProvider {

    /**
     * Blocking, must be called from a background thread.
     *
     * @param incremental Boolean true to import the increments, false to import all data
     * @param progress (current: Int, max: Int) -> Unit called for each imported entry
     * @return AbstractCloudProvider.SyncResult
     */
    fun synchronize(incremental: Boolean, progress: (Int, Int) -> Unit): AbstractCloudProvider.SyncResult
}

/**
 * Abstract class for CloudProviders
 *
 * @param context Context the application context
 * @param r Resources
 * @param factory IndexWriterFactory
 * @param token String the provider's access token (or connection string)
 */
abstract class AbstractCloudProvider(context: Context, r: Resources, factory: IndexWriterFactory, token: String): CloudProvider {
    private val mContext = context
    private val mResources = r
    private val mIndexWriterFactory = factory
    private val mToken = token

    protected fun getContext(): Context = mContext
    protected fun getResources(): Resources = mResources
    protected fun getIndexWriterFactory(): IndexWriterFactory = mIndexWriterFactory
    protected fun getToken(): String = mToken

    protected fun sendNoIncremental(): SyncResult {
        AbstractCloudProvider.switchIncrementalVal(mContext)
        return SyncResult(false, mResources.getString(R.string.abstract_cloud_provider_no_incremental))
    }

    companion object {

        fun switchIncrementalVal(context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preferences.edit()
            editor.putBoolean(SYNC_SWITCH_VALUE_INCR, false)
            editor.apply()
        }
    }

//    ---

    class SyncResult(val value: Boolean, val message: String) {
        fun getResult(): Boolean {
            return value
        }
    }
}
