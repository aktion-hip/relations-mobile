package org.elbe.relations.mobile.preferences

import android.content.Context
import android.support.v7.preference.DialogPreference
import android.util.AttributeSet
import org.elbe.relations.mobile.R

/**
 * Preference class displaying the Relations app info dialog.
 */
class AppInfo: DialogPreference {
    private val mDialogLayoutResId = R.layout.pref_dialog_about

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, R.attr.dialogPreferenceStyle)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes)

    override fun getDialogLayoutResource(): Int {
        return mDialogLayoutResId
    }

}