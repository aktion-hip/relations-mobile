package org.elbe.relations.mobile.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Html
import android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
import android.text.Spanned
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.Item
import org.elbe.relations.mobile.model.MinItem
import java.io.Serializable
import java.text.DateFormat
import java.text.SimpleDateFormat

val DATE_FORMAT : DateFormat = SimpleDateFormat("dd.MM.yy")

/**
 * Fragment class to display the item's details.
 */
class ItemDetailsFragment : Fragment() {
    private var item: Serializable? = null
    private var isDualPane: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            item = it.getSerializable(ARG_PARAM)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view = inflater!!.inflate(R.layout.fragment_item_details, container, false)
        return view
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
                    fragmentManager?.beginTransaction()?.replace(relatedFragment!!.id, related)?.commit()
                }
            }
        }
    }

// --- fragment interaction

    interface OnFragmentInteractionListener {
        fun onShowFragment(item: Item)
    }

    companion object {
        private const val ARG_PARAM = "item"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Serializable
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
