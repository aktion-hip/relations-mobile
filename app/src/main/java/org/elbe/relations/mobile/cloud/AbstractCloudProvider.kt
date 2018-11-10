package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.MainActivity
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.elbe.relations.mobile.util.ProgressDialog

/**
 * Abstract class for CloudProviders
 */
abstract class AbstractCloudProvider<Params, Progress, Result>(incremental: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AsyncTask<Void, Int, AbstractCloudProvider.SyncResult>() {
    private val mIncremental = incremental
    private val mContext = context
    private val mResources = r
    private var mDialogProgress: ProgressDialog? = null
    private val mIndexWriterFactory = factory
    private var mToken = ""

    protected fun isIncremental(): Boolean = mIncremental
    protected fun getContext(): AppCompatActivity = mContext
    protected fun getResources(): Resources = mResources
    protected fun getDialogProgress(): ProgressDialog? = mDialogProgress
    protected fun getIndexWriterFactory(): IndexWriterFactory = mIndexWriterFactory

    fun setToken(token: String): AbstractCloudProvider<Params, Progress, Result> {
        mToken = token
        return this
    }

    protected fun getToken(): String = mToken

    override fun onPreExecute() {
        mDialogProgress = ProgressDialog.newInstance(mResources.getString(R.string.abstract_cloud_provider_dialog_title1))
        mDialogProgress?.let {
            it.isCancelable = false
            it.show(getContext().supportFragmentManager, "fragment_download")
        }
    }

    override fun onProgressUpdate(vararg values: Int?) {
        val max = values[1] ?: 0
        if (max > 0) {
            if (mDialogProgress?.isBar() == true) {
                mDialogProgress?.increment()
            } else {
                mDialogProgress?.switchToBar(max)
                mDialogProgress?.setTitle(mResources.getString(R.string.abstract_cloud_provider_dialog_title2))
            }
        }
    }

    override fun onPostExecute(result: SyncResult?) {
        mDialogProgress?.let {progress ->
            progress.finish(result?.message ?: mResources.getString(R.string.abstract_cloud_provider_dft_error))
            progress.dismiss()
        }
        refresh()
    }

    private fun refresh() {
        mContext.finish()
        mContext.startActivity(Intent(mContext, MainActivity::class.java))
    }

    protected fun sendNoIncremental(): SyncResult {
        AbstractCloudProvider.switchIncrementalVal(mContext)
        return SyncResult(false, mResources.getString(R.string.abstract_cloud_provider_no_incremental))
    }

    companion object {

        fun switchIncrementalVal(context: AppCompatActivity) {
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