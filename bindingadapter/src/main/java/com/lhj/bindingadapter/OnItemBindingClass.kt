package com.lhj.bindingadapter

import androidx.annotation.LayoutRes
import com.lhj.bindingadapter.itembindings.OnItemBindClass

inline fun <reified T : Any> OnItemBindClass<Any>.map(
    noinline onItemBind: (
        @ParameterName("itemBinding") ItemBinding<in T>,
        @ParameterName("position") Int,
        @ParameterName("item") T
    ) -> Unit
): OnItemBindClass<Any> = map(T::class.java) { itemBinding, position, item ->
    onItemBind(
        itemBinding as ItemBinding<in T>,
        position,
        item
    )
}

inline fun <reified E : Any> OnItemBindClass<Any>.map(
    variableId: Int,
    @LayoutRes layoutRes: Int
): OnItemBindClass<Any> =
    map(E::class.java, variableId, layoutRes)