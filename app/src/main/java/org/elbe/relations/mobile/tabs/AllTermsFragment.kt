package org.elbe.relations.mobile.tabs

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
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
    private var mHelper: RetrieveListHelper? = null
    private val mUiHandler = Handler(Looper.getMainLooper())
    private var mAdapter: ItemAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_all_terms, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        mHelper?.run(Runnable {
            val terms = mHelper?.getListOf(Type.TERM)
            val emptyText = view.findViewById<TextView>(R.id.items_term_empty)
            if (terms?.size != 0) {
                mUiHandler.post {
                    emptyText.visibility = View.GONE
                    val recyclerView = view.findViewById<RecyclerView>(R.id.items_term)
                    recyclerView.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
                    mAdapter = ItemAdapter(activity, terms)
                    recyclerView.adapter = mAdapter
                    ItemTouchHelper(ItemSwipeHelper(recyclerView, activity)).attachToRecyclerView(recyclerView)
                }
            }
        })
    }

    override fun onAttach(context: Context) {
        mHelper = RetrieveListHelper(context, "allTerms")
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
        mHelper?.quit()
        mHelper = null
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
