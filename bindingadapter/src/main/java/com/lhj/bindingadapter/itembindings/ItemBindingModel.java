package com.lhj.bindingadapter.itembindings;

import androidx.annotation.NonNull;

import com.lhj.bindingadapter.ItemBinding;

/**
 * Implement this interface on yor items to use with {@link OnItemBindModel}.
 */
public interface ItemBindingModel {
    /**
     * Set the binding variable and layout of the given view.
     * <pre>{@code
     * onItemBind.set(BR.item, R.layout.item);
     * }</pre>
     */
    void onItemBind(@NonNull ItemBinding itemBinding);
}
