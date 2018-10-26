package org.elbe.relations.mobile.ui

import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.Item
import org.elbe.relations.mobile.model.MinItem
import org.elbe.relations.mobile.util.ItemSwipeHelper
import org.elbe.relations.mobile.util.RelationsHelper
import java.io.Serializable

/**
 * Fragment to display an item's list of related items.
 */
class ItemRelatedFragment : Fragment() {
    private lateinit var related: List<Item>
    private lateinit var helper: RelationsHelper
    private val uiHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper = RelationsHelper(context)

        if (arguments != null) {
            helper.run(Runnable {
                var item = arguments?.getSerializable(ARG_PARAM)
                related = helper.getRelated(item as MinItem)
                uiHandler.post {
                    activity?.findViewById<RecyclerView>(R.id.itemsRelated)?.apply {
                        layoutManager = LinearLayoutManager(activity, LinearLayout.VERTICAL, false)
                        var itemAdapter = ItemAdapter(activity, related)
                        adapter = itemAdapter
                        ItemTouchHelper(ItemSwipeHelper(this, activity)).attachToRecyclerView(this)
                    }
                }
            })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.fragment_item_related, container, false)
    }

    override fun onDestroy() {
        helper.quit()
        super.onDestroy()
    }

//    --- fragment interaction

    companion object {
        private const val ARG_PARAM = "item"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param item Serializable
         * @return A new instance of fragment ItemRelatedFragment.
         */
        fun newInstance(item: Serializable?): ItemRelatedFragment {
            val fragment = ItemRelatedFragment()
            val args = Bundle()
            args.putSerializable(ARG_PARAM, item)
            fragment.arguments = args
            return fragment
        }
    }
}// Required empty public constructor
