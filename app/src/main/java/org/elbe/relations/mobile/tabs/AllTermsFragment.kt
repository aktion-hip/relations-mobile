package org.elbe.relations.mobile.tabs

import android.content.Context
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
import android.widget.TextView
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.Type
import org.elbe.relations.mobile.ui.ItemAdapter
import org.elbe.relations.mobile.util.ItemSwipeHelper
import org.elbe.relations.mobile.util.RetrieveListHelper

/**
 * Fragment to display all terms on the main activity.
 */
class AllTermsFragment : Fragment() {
    private var helper: RetrieveListHelper? = null
    private val uiHandler = Handler()

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_all_terms, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        helper?.run(Runnable {
            var terms = helper?.getListOf(Type.TERM)
            val emptyText = view.findViewById<TextView>(R.id.items_term_empty)
            if (terms?.size != 0) {
                uiHandler.post({
                    emptyText.visibility = View.GONE
                    val recyclerView = view.findViewById<RecyclerView>(R.id.items_term)
                    recyclerView.layoutManager = LinearLayoutManager(activity, LinearLayout.VERTICAL, false)
                    var itemAdapter = ItemAdapter(activity, terms)
                    recyclerView.adapter = itemAdapter
                    ItemTouchHelper(ItemSwipeHelper(recyclerView, activity)).attachToRecyclerView(recyclerView)
                })
            }
        })
    }

    override fun onAttach(context: Context?) {
        helper = RetrieveListHelper(context!!, "allTerms")
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
        helper?.quit()
        helper = null
    }

    companion object {

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment AllTermsFragment.
         */
        fun newInstance(): AllTermsFragment {
            return AllTermsFragment()
        }
    }
}// Required empty public constructor
