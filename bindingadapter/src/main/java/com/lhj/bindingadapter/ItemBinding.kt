package com.lhj.bindingadapter

import androidx.annotation.LayoutRes
import com.lhj.bindingadapter.itembindings.OnItemBindClass


inline fun <T> itemBindingOf(variableId: Int, @LayoutRes layoutRes: Int): ItemBinding<T> =
    ItemBinding.of(variableId, layoutRes)

inline fun <T> itemBindingOf(
    noinline onItemBind: (
        @ParameterName("itemBinding") ItemBinding<in T>,
        @ParameterName("position") Int,
        @ParameterName("item") T
    ) -> Unit
): ItemBinding<T> = ItemBinding.of(onItemBind)

fun itemBindingClassOf(): OnItemBindClass<Any>{
    return OnItemBindClass<Any>()
}

inline fun <T> OnItemBind<T>.toItemBinding(): ItemBinding<T> =
    ItemBinding.of(this)
