package org.elbe.relations.mobile.util

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.elbe.relations.mobile.R

/**
 * Utility class to display the progress bar in a dialog.
 */
class ProgressDialog(): DialogFragment() {
    private var mView: View? = null
    private var count: Int = 0
    private var maxValue: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mView = inflater.inflate(R.layout.fragment_progress, container)
        return mView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {arguments ->
            maxValue = arguments.getInt("maxValue")
            if (maxValue == 0) {
                getBar()?.visibility = View.GONE
                getSpinner()?.visibility = View.VISIBLE
            } else {
                getBar()?.max = maxValue
            }
            val title = arguments.getString("title")
            if (!title.isEmpty()) {
                setTitle(title)
            }
        }
    }

    private fun getBar(): ProgressBar? {
        return mView?.findViewById(R.id.progress_bar)
    }

    private fun getSpinner(): ProgressBar? {
        return mView?.findViewById(R.id.progress_indeterminate)
    }

    /**
     * @param title String the progress dialog's new text to display
     */
    fun setTitle(title: String) {
        mView?.findViewById<TextView>(R.id.progress_count)?.text = title
    }

    /**
     * Switch the progress dialog from spinner to bar.
     */
    fun switchToBar(maxVal: Int) {
        getSpinner()?.visibility = View.GONE
        maxValue = maxVal
        with(getBar()) {
            this?.max = maxValue
            this?.visibility = View.VISIBLE
        }
    }

    /**
     * @return Boolean true if the dialog shows the progress bar
     */
    fun isBar(): Boolean {
        return getBar()?.visibility == View.VISIBLE
    }

    /**
     * Increments the progress bar by one tick.
     */
    fun increment() {
        count +=1
        getBar()?.progress = count
    }

    /**
     * Finish the ProgressBar.
     *
     * @param msg: String the success message to display in a Toast.
     */
    fun finish(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

//    ---
    companion object {

        /**
         * Show progress bar with max ticks
         *
         * @param max Int the max value for the progress bar
         * @param title String the title to display on the progress dialog
         */
        fun newInstance(max: Int, title: String = ""): ProgressDialog {
            val fragment = ProgressDialog()
            val args = Bundle()
            args.putInt("maxValue", max)
            args.putString("title", title)
            fragment.arguments = args
            return fragment
        }

        /**
         * Show indeterminate progress.
         *
         * @param title String the title to display on the progress dialog
         */
        fun newInstance(title: String): ProgressDialog {
            return newInstance(0, title)
        }
    }
}