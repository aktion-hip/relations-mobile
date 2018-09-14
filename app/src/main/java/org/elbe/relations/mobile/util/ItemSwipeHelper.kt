package org.elbe.relations.mobile.util

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import kotlinx.android.synthetic.main.item.view.*
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.ui.ItemAdapter

/**
 * Helper class to initialize the swipe gesture on a recycler view displaying the list of items.
 */
class ItemSwipeHelper(recyclerView: RecyclerView, context: Activity?): ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
    private val background : Drawable = ColorDrawable()
    private val mRecyclerView = recyclerView
    private val mContext = context

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val adapter = mRecyclerView?.adapter
        if (adapter is ItemAdapter) {
            adapter.handleItem(viewHolder.adapterPosition)
        }
    }

    override fun onChildDraw(c: Canvas?, recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        val itemView = viewHolder?.itemView

        if (background is ColorDrawable) {
            mContext?.applicationContext?.let {context ->
                background.color = ContextCompat.getColor(context, R.color.colorPrimary)
            }
        }
        background.setBounds(itemView!!.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
        background.draw(c)

        //Setting Swipe Text
        val textSize : Float = viewHolder.itemView.itemTitle.textSize
        val label = mContext?.resources?.getString(R.string.entry_show_details)
        val paint = Paint()
        paint.color = Color.WHITE
        paint.textSize = textSize
        paint.textAlign = Paint.Align.CENTER
        val textWidth = paint.measureText(label)
        val rect = RectF(itemView.right.toFloat(), itemView.top.toFloat(), itemView.left.toFloat(), itemView.bottom.toFloat())
        c?.drawText(label, rect.centerX() - (textWidth/2) + 200, rect.centerY() + (textSize/2), paint)

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) {
        super.clearView(recyclerView, viewHolder)
    }

}