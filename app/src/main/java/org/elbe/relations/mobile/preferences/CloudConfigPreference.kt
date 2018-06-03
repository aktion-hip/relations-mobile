package org.elbe.relations.mobile.preferences

import android.content.Context
import android.content.res.TypedArray
import android.support.v7.preference.EditTextPreference
import android.util.AttributeSet
import org.elbe.relations.mobile.R

/**
 * Custom preference to display the list of cloud configuration entries.
 *
 * @see https://medium.com/@JakobUlbrich/building-a-settings-screen-for-android-part-3-ae9793fd31ec
 * @see https://android.googlesource.com/platform/frameworks/support/+/6904f67/v7/preference/src/android/support/v7/preference
 */
class CloudConfigPreference: EditTextPreference {
    val mDialogLayoutResId = R.layout.pref_dialog_cloud_config
    var mCloudProviderId: String = ""

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, R.attr.dialogPreferenceStyle)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes)

    /**
     * Persists the value of the cloud configuration.
     */
    fun setCloudConfig(cloudProviderId: String) {
        mCloudProviderId = cloudProviderId
        persistString(cloudProviderId)
    }

    fun getCloudConfig(): String {
        return mCloudProviderId
    }

    override fun getDialogLayoutResource(): Int {
        return mDialogLayoutResId
    }

    override fun onGetDefaultValue(a: TypedArray?, index: Int): Any {
        return a?.getString(index) ?: ""
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        val dft = defaultValue?.toString() ?: ""
        setCloudConfig(if (restoreValue) getPersistedString(mCloudProviderId) else dft)
    }

}