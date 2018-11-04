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
 * Fragment to display all texts on the main activity.
 */
class AllTextsFragment : Fragment() {
    private var mHelper: RetrieveListHelper? = null
    private val mUiHandler = Handler()
    private var mAdapter: ItemAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view =  inflater.inflate(R.layout.fragment_all_texts, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        mHelper?.run(Runnable {
            val texts = mHelper?.getListOf(Type.TEXT)
            val emptyText = view.findViewById<TextView>(R.id.items_text_empty)
            if (texts?.size != 0) {
                mUiHandler.post {
                    emptyText.visibility = View.GONE
                    val recyclerView = view.findViewById<RecyclerView>(R.id.items_text)
                    recyclerView.layoutManager = LinearLayoutManager(activity, LinearLayout.VERTICAL, false)
                    mAdapter = ItemAdapter(activity, texts)
                    recyclerView.adapter = mAdapter
                    ItemTouchHelper(ItemSwipeHelper(recyclerView, activity)).attachToRecyclerView(recyclerView)
                }
            }
        })
    }


    override fun onAttach(context: Context?) {
        mHelper = RetrieveListHelper(context!!, "allTexts")
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
         */
        fun newInstance(): AllTextsFragment {
            return AllTextsFragment()
        }
    }
}// Required empty public constructor
