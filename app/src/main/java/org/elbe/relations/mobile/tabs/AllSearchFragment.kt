package org.elbe.relations.mobile.tabs

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.ui.ItemAdapter
import org.elbe.relations.mobile.util.ItemSwipeHelper
import kotlinx.android.synthetic.main.fragment_all_search.*
import org.elbe.relations.mobile.search.SearchCache

/**
 * Fragment to show the search results.
 */
class AllSearchFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_all_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (SearchCache.isEmpty()) {
            search_empty?.let {
                if (it.visibility == View.GONE) {
                    it.visibility = View.VISIBLE
                }
            }
        } else {
            search_empty?.let {
                it.visibility = View.GONE
            }
            items_search?.let {list ->
                list.clearOnChildAttachStateChangeListeners()

                list.layoutManager = LinearLayoutManager(context, LinearLayout.VERTICAL, false)
                val itemAdapter = ItemAdapter(context, SearchCache.getResult())
                list.adapter = itemAdapter

                val itemTouchHelper = ItemTouchHelper(ItemSwipeHelper(list, activity))
                itemTouchHelper.attachToRecyclerView(list)
            }
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment AllSearchFragment.
         */
        fun newInstance(): AllSearchFragment {
            return AllSearchFragment()
        }
    }
}// Required empty public constructor
