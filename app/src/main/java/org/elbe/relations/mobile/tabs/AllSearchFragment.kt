package org.elbe.relations.mobile.tabs

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.elbe.relations.mobile.databinding.FragmentAllSearchBinding
import org.elbe.relations.mobile.ui.ItemAdapter
import org.elbe.relations.mobile.util.ItemSwipeHelper
import org.elbe.relations.mobile.search.SearchCache

/**
 * Fragment to show the search results.
 */
class AllSearchFragment : Fragment() {
    private var mBinding: FragmentAllSearchBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAllSearchBinding.inflate(inflater, container, false)
        mBinding = binding
        return binding.root
    }

    override fun onDestroyView() {
        mBinding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (SearchCache.isEmpty()) {
            mBinding?.searchEmpty?.let {
                if (it.visibility == View.GONE) {
                    it.visibility = View.VISIBLE
                }
            }
        } else {
            mBinding?.searchEmpty?.let {
                it.visibility = View.GONE
            }
            mBinding?.itemsSearch?.let {list ->
                list.clearOnChildAttachStateChangeListeners()

                list.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
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
