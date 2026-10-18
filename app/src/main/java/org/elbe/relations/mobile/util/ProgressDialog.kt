package org.elbe.relations.mobile.util

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import org.elbe.relations.mobile.R

/**
 * Utility class to display the progress bar in a dialog.
 */
class ProgressDialog: DialogFragment() {
    private var mView: View? = null
    private var count: Int = 0
    private var maxValue: Int = 0
    private var pendingTitle: String? = null
    private var mTitle: String? = null
    private var mShowCancel = false
    private var mDetail: String? = null

    /** Called when the user taps Cancel, see showCancel(). */
    var onCancel: (() -> Unit)? = null

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
            val title = arguments.getString("title") ?: ""
            if (title.isNotEmpty()) {
                setTitle(title)
            }
        }
        // title, cancel and progress set before the view existed
        mTitle?.let { setTitle(it) }
        showDetail(mDetail)
        view.findViewById<View>(R.id.progress_cancel)?.setOnClickListener { onCancel?.invoke() }
        showCancel(mShowCancel)
        if (count > 0 && maxValue > 0) {
            showProgress(count, maxValue, pendingTitle ?: "")
        }
    }

    /**
     * @param detail String? the (selectable) text below the title, null to hide it
     */
    fun showDetail(detail: String?) {
        mDetail = detail
        mView?.findViewById<TextView>(R.id.progress_detail)?.let {
            it.text = detail ?: ""
            it.visibility = if (detail == null) View.GONE else View.VISIBLE
        }
    }

    /**
     * @param show Boolean true to display the Cancel button
     */
    fun showCancel(show: Boolean) {
        mShowCancel = show
        mView?.findViewById<View>(R.id.progress_cancel)?.visibility = if (show) View.VISIBLE else View.GONE
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
        mTitle = title
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
     * Displays the progress in the bar, switches from spinner to bar if needed.
     *
     * @param current Int the number of processed ticks
     * @param max Int the number of ticks
     * @param title String the title to display with the bar
     */
    fun showProgress(current: Int, max: Int, title: String) {
        count = current
        pendingTitle = title
        if (mView == null) {
            maxValue = max
            return
        }
        if (!isBar() || maxValue != max) {
            switchToBar(max)
            setTitle(title)
        }
        getBar()?.progress = count
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