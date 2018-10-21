package org.elbe.relations.mobile.ui

import android.content.Context
import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import org.elbe.relations.mobile.EXTRA_ITEM
import org.elbe.relations.mobile.model.MinItem

/**
 * Listener for touch events on items in the RecyclerView.
 */
class RecyclerTouchListener(context: Context, recyclerView: RecyclerView, clickListener: ClickListener): RecyclerView.OnItemTouchListener {
    private val mGestureDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent?) {
            e?.let {
                val child = recyclerView.findChildViewUnder(it.x, it.y)
                child?.let {child ->
                    clickListener.onLongClick(child, recyclerView.getChildAdapterPosition(child))
                }
            }
        }

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            return false
        }
    })

    override fun onInterceptTouchEvent(rv: RecyclerView?, e: MotionEvent?): Boolean {
        e?.let {
            mGestureDetector.onTouchEvent(it)
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView?, e: MotionEvent?) {
        // nothing to do
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // nothing to do
    }

    companion object {
        /**
         * Static function to add an item touch listener to the specified recycler view.
         */
        fun addOnItemTouchListener(context: Context, recyclerView: RecyclerView, list: List<MinItem>) {
            recyclerView.addOnItemTouchListener(RecyclerTouchListener(context, recyclerView, object : ClickListener {
                override fun onLongClick(view: View, position: Int) {
                    val intent = Intent(context, ShowItemActivity::class.java).apply {
                        putExtra(EXTRA_ITEM, list[position])
                    }
                    context.startActivity(intent)
                }
            }))
        }
    }
}

interface ClickListener {
    fun onLongClick(view: View, position: Int)
}