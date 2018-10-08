package org.elbe.relations.mobile.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Html
import android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
import android.text.Spanned
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.Item
import org.elbe.relations.mobile.model.MinItem
import java.io.Serializable
import java.text.DateFormat
import java.text.SimpleDateFormat

private val DATE_FORMAT : DateFormat = SimpleDateFormat("dd.MM.yy")
private const val ARG_PARAM = "item"
private const val TAG = "ItemDetailsFragment"

/**
 * Fragment class to display the item's details.
 */
class ItemDetailsFragment : Fragment() {
    private var item: Serializable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            item = it.getSerializable(ARG_PARAM)
        }
        Log.v(TAG, "Fragment created.")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.fragment_item_details, container, false)
    }

    @SuppressWarnings("deprecation")
    private fun fromHtml(item: Item): Spanned? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(item.getDetailText(resources), FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
        }
        return Html.fromHtml(item.getDetailText(resources))
    }

    private fun showItem(item: Serializable?, view: View?) {
        if (item is Item) {
            view?.findViewById<TextView>(R.id.itemDetailTitle)?.apply {
                text = item.getTitle()
            }
            view?.findViewById<TextView>(R.id.itemDetailDate)?.apply {
                text = if (isLandscape()) shortDate(item) else item.getCreated(resources)
            }
            view?.findViewById<TextView>(R.id.itemDetailText)?.apply {
                text = fromHtml(item)
            }
        }
    }

    private fun shortDate(item: Item): String {
        return "${resources.getString(R.string.item_created)}: ${DATE_FORMAT.format(item.getCreationDate())};  " +
                "${resources.getString(R.string.item_modified)}: ${DATE_FORMAT.format(item.getMutationDate())}"
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        item?.let { item ->
            showItem(item, view)

            if (isLandscape()) {
                var related = fragmentManager?.findFragmentById(R.id.fragmentItemRelated)
                if (related == null) {
                    related = ItemRelatedFragment.newInstance(item)
                    val relatedFragment = activity?.findViewById<FrameLayout>(R.id.activity_details_related_container)
                    relatedFragment?.let {fragment ->
                        fragmentManager?.beginTransaction()?.replace(fragment.id, related)?.commit()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        item?.let { item ->
            outState.putSerializable(ARG_PARAM, item)
            if (item is MinItem) {
                Log.v(TAG, "Fragment saved with item '${item.getTitle()}' (id: ${item.getId()}).")
            }
        }
    }

    companion object {

        /**
         * Factory method to create a new instance to display the specified item.
         *
         * @param item MinItem?
         * @return A new instance of fragment ItemDetailsFragment.
         */
        fun newInstance(item: MinItem?): ItemDetailsFragment {
            val fragment = ItemDetailsFragment()
            val args = Bundle()
            args.putSerializable(ARG_PARAM, item)
            fragment.arguments = args
            return fragment
        }
    }

}
