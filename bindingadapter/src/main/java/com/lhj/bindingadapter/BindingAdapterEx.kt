package com.lhj.bindingadapter

import androidx.databinding.ViewDataBinding

fun <T> bindingAdapter(
    onBindBinding: (
        binding: ViewDataBinding,
        variableId: Int,
        layoutRes: Int,
        position: Int,
        item: T,
        adapter:BindingRecyclerViewAdapter<T>
    ) -> Unit
): BindingRecyclerViewAdapter<T> {
    val adapter = object : BindingRecyclerViewAdapter<T>() {
        override fun onBindBinding(
            binding: ViewDataBinding,
            variableId: Int,
            layoutRes: Int,
            position: Int,
            item: T
        ) {
            try {
                super.onBindBinding(binding, variableId, layoutRes, position, item)
                onBindBinding.invoke(binding, variableId, layoutRes, position, item,this)
            }catch (_:Exception){}
        }
    }
    return adapter
}