package org.elbe.relations.mobile.preferences

import android.os.Bundle
import android.os.Handler
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.view.View
import android.widget.TextView
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.util.AboutInfoHelper

/**
 * The fragment to display the Relations about info.
 */
class AppInfoFragment(): PreferenceDialogFragmentCompat() {
    private val mUIHandler = Handler()
    private val mHelper: AboutInfoHelper by lazy {
        AboutInfoHelper(context)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        mHelper.quit()
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)

        mHelper.run(Runnable {
            mHelper.initialize()
            mUIHandler.post({
                val total = view?.findViewById<TextView>(R.id.countDbTotalVal)
                total?.text = mHelper.getCountTotal()
                val terms = view?.findViewById<TextView>(R.id.countDbTermsVal)
                terms?.text = mHelper.getCountTerms()
                val texts = view?.findViewById<TextView>(R.id.ccountDbTextsVal)
                texts?.text = mHelper.getCountTexts()
                val persons = view?.findViewById<TextView>(R.id.countDbPersonsVal)
                persons?.text = mHelper.getCountPersons()
                val relations = view?.findViewById<TextView>(R.id.countDbRelationsVal)
                relations?.text = mHelper.getCountRelations()
                val indexed = view?.findViewById<TextView>(R.id.countSearchIndexVal)
                indexed?.text = mHelper.getNumberOfIndexed()
            })
        })

    }

//    ---

    companion object {
        fun newInstance(): AppInfoFragment {
            val fragment = AppInfoFragment()
            val args = Bundle()
            args.putString(ARG_KEY, "preference_about_view")
            fragment.arguments = args
            return fragment
        }
    }

}