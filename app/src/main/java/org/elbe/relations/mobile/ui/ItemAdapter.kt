@file:Suppress("NAME_SHADOWING")
package org.elbe.relations.mobile.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.preference.PreferenceManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.elbe.relations.mobile.EXTRA_ITEM
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.MinItem

private const val TAG = "ItemAdapter"
private const val DURATION = 40L

/**
 * Adapter to display the list of items.
 */
class ItemAdapter(context: Context?, items : List<MinItem>?) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {
    private val mContext = context
    private val mItems: List<MinItem>? = items
    private val mShowItem: (Int, Context?, List<MinItem>?) -> Unit = { position, context, items ->
        val intent = Intent(context, ShowItemActivity::class.java).apply {
            putExtra(EXTRA_ITEM, items?.get(position))
        }
        context?.startActivity(intent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.let {holder ->
            mItems?.let {list ->
                holder.itemTitle.text = list[position].getTitle()
                holder.itemImage.setImageResource(list[position].getType().icon)
            }
        }
    }

    override fun getItemCount(): Int {
        return mItems?.size ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return ViewHolder(v, mItems, mShowItem)
    }

    fun handleItem(position: Int) {
        mShowItem(position, mContext, mItems)
        notifyItemRemoved(position)
        notifyItemInserted(position)
    }

    // --- inner classes ---

    class ViewHolder(itemView: View, items : List<MinItem>?, showItem: (Int, Context?, List<MinItem>?) -> Unit) : RecyclerView.ViewHolder(itemView), View.OnLongClickListener  {
        val itemTitle: TextView = itemView.findViewById(R.id.itemTitle)
        val itemImage: ImageView = itemView.findViewById(R.id.itemImage)
        private val mItems = items
        private val mShowItem = showItem
        private val mVibrator = itemView.context.getSystemService(Context.VIBRATOR_SERVICE)

        init {
            itemView.setOnLongClickListener(this)
        }

        override fun onLongClick(view: View?): Boolean {
            view?.let {view ->
                val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
                if (preferences.getBoolean("preference_settings_long_press", true)) {
                    Log.v(TAG, "ViewHolder.onLongClick: about to show item on position=$adapterPosition!")
                    vibrate(mVibrator)
                    mShowItem(adapterPosition, view.context, mItems)
                }
            }
            return true
        }

        @Suppress("DEPRECATION")
        private fun vibrate(vibrator: Any?) {
            if (vibrator is Vibrator) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(DURATION, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(DURATION)
                }
            }
        }

    }
}