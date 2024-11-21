package com.example.myutil

import androidx.databinding.ObservableList
import androidx.databinding.ObservableList.OnListChangedCallback
import androidx.recyclerview.widget.RecyclerView

/**
 * DynamicChangeCallback 通过ObservableArrayList.addOnListChangedCallback()实现RecyclerView界面自动刷新
 */
class DynamicRecyclerViewChangeCallback<T>(private val adapter: RecyclerView.Adapter<*>) :
    OnListChangedCallback<ObservableList<T>>() {

    override fun onChanged(sender: ObservableList<T>) {
        adapter.notifyDataSetChanged()
    }

    override fun onItemRangeChanged(
        sender: ObservableList<T>,
        positionStart: Int,
        itemCount: Int
    ) {
        adapter.notifyItemRangeChanged(positionStart, itemCount)
    }

    override fun onItemRangeInserted(
        sender: ObservableList<T>,
        positionStart: Int,
        itemCount: Int
    ) {
        adapter.notifyItemRangeInserted(positionStart, itemCount)
    }

    override fun onItemRangeMoved(
        sender: ObservableList<T>,
        fromPosition: Int,
        toPosition: Int,
        itemCount: Int
    ) {
        adapter.notifyItemRangeRemoved(fromPosition, itemCount)
        adapter.notifyItemRangeInserted(toPosition, itemCount)
    }

    override fun onItemRangeRemoved(
        sender: ObservableList<T>,
        positionStart: Int,
        itemCount: Int
    ) {
        adapter.notifyItemRangeRemoved(positionStart, itemCount)
    }
}