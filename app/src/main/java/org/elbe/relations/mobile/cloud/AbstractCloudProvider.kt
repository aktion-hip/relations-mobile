package org.elbe.relations.mobile.cloud

import android.content.res.Resources
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import org.elbe.relations.mobile.search.IndexWriterFactory
import org.elbe.relations.mobile.util.ProgressDialog

/**
 * Abstract class for CloudProviders
 */
abstract class AbstractCloudProvider<Params, Progress, Result>(synchronize: Boolean, context: AppCompatActivity, r: Resources, factory: IndexWriterFactory):
        AsyncTask<Params, Progress, Result>() {
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
}