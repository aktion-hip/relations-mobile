package org.elbe.relations.mobile.cloud

import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.MainActivity
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.elbe.relations.mobile.util.ProgressDialog

/**
 * Abstract class for CloudProviders
 */
abstract class AbstractCloudProvider<Params, Progress, Result>(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AsyncTask<Void, Int, AbstractCloudProvider.SyncResult>() {
    private val mSynchronize = synchronize
    private val mContext = context
    private val mResources = r
    private var mDialogProgress: ProgressDialog? = null
    private val mIndexWriterFactory = factory
    private var mToken = ""

    protected fun getSynchronize(): Boolean = mSynchronize
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
        mDialogProgress = ProgressDialog.newInstance("Download data.")
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
                mDialogProgress?.setTitle("Import data.")
            }
        }
    }

    override fun onPostExecute(result: SyncResult?) {
        mDialogProgress?.finish(result?.message ?: "An error occurred during the import.")
        mDialogProgress?.dismiss()
        refresh()
    }

    private fun refresh() {
        mContext.finish()
        mContext.startActivity(Intent(mContext, MainActivity::class.java))
    }

//    ---

    class SyncResult(val value: Boolean, val message: String) {
        fun getResult(): Boolean {
            return value
        }
    }
}