package org.elbe.relations.mobile.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
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

/**
 * Fragment class to display the item's details.
 */
class ItemDetailsFragment : Fragment() {
    private var item: Serializable? = null
    private var isDualPane: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            item = arguments.getSerializable(ARG_PARAM)
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view = inflater!!.inflate(R.layout.fragment_item_details, container, false)
        if (item != null) {
            showItem(item, view)
        }
        return view
    }

    fun showItem(item: Serializable?, view: View) {
        if (item is Item) {
            view.findViewById<TextView>(R.id.itemDetailTitle).apply {
                text = item.getTitle()
            }
            view.findViewById<TextView>(R.id.itemDetailDate).apply {
                text = item.getCreated(resources)
            }
            view.findViewById<TextView>(R.id.itemDetailText).apply {
                text = fromHtml(item)
            }
        }
    }

    @SuppressWarnings("deprecation")
    private fun fromHtml(item: Item): Spanned? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(item.getDetailText(resources), FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
        }
        return Html.fromHtml(item.getDetailText(resources))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val relatedFrame = activity.findViewById<FrameLayout>(R.id.item_related)
        isDualPane = relatedFrame != null && relatedFrame.visibility == View.VISIBLE

        if (savedInstanceState != null) {
        }
        if (isDualPane) {
            var related = fragmentManager.findFragmentById(R.id.fragmentItemRelated)
            if (related == null) {
                related = ItemRelatedFragment.newInstance(item)
                fragmentManager.beginTransaction().replace(R.id.item_relate_fragment_container, related).
                        setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).commit()
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
