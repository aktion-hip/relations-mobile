package org.elbe.relations.mobile.ui

import android.content.Context
import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.elbe.relations.mobile.EXTRA_ITEM
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.MinItem

/**
 * Adapter to display the list of items.
 *
 * Created by lbenno on 02.03.2018.
 */
class ItemAdapter(context: Context, items : List<MinItem>?) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {
    private val mContext = context
    private val mItems: List<MinItem>? = items

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.let {holder ->
            mItems?.let {list ->
                holder.itemTitle.text = list[position].getTitle()
                holder.itemImage.setImageResource(list[position].getType().icon)
            }
        }
    }

    override fun getItemCount(): Int {
        return if (mItems == null) 0 else mItems!!.size
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent?.context).inflate(R.layout.item, parent, false)
        return ViewHolder(v)
    }

    fun handleItem(position: Int) {
        val intent = Intent(mContext, ShowItemActivity::class.java).apply {
            putExtra(EXTRA_ITEM, mItems?.get(position))
        }
        mContext?.startActivity(intent)
        notifyItemRemoved(position)
        notifyItemInserted(position)
    }

    // --- inner classes ---

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        val itemTitle: TextView = itemView.findViewById<TextView>(R.id.itemTitle)
        val itemImage: ImageView = itemView.findViewById<ImageView>(R.id.itemImage)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            print(v)
        }
    }
}