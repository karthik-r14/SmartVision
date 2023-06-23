package com.mobileassistant.smartvision.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.model.MenuItem

class DashboardAdapter(context: Context, items: List<MenuItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<MenuItem> = ArrayList()
    private val ctx: Context
//    private var mOnItemClickListener: OnItemClickListener? = null

//    interface OnItemClickListener {
//        fun onItemClick(view: View?, obj: ShopCategory?, position: Int)
//    }
//
//    fun setOnItemClickListener(mItemClickListener: OnItemClickListener?) {
//        mOnItemClickListener = mItemClickListener
//    }

    init {
        this.items = items
        ctx = context
    }

    inner class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var image: ImageView
        var title: TextView
        var tileLayout: View

        init {
            image = view.findViewById(R.id.image) as ImageView
            title = view.findViewById(R.id.title) as TextView
            tileLayout = view.findViewById(R.id.tileLayout) as View
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val viewHolder: RecyclerView.ViewHolder
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.item_grid_card, parent, false)
        viewHolder = TileViewHolder(view)
        return viewHolder
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TileViewHolder) {
            val item: MenuItem = items[position]
            holder.title.text = item.menuText
            holder.image.setImageDrawable(holder.image.context.resources.getDrawable(item.iconResId))
//            view.tileLayout.setOnClickListener { view ->
//                if (mOnItemClickListener != null) {
//                    mOnItemClickListener!!.onItemClick(view, items[position], position)
//                }
//            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}