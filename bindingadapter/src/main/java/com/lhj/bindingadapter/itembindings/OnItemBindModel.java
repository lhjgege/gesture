package com.lhj.bindingadapter.itembindings;

import androidx.annotation.NonNull;

import com.lhj.bindingadapter.ItemBinding;
import com.lhj.bindingadapter.OnItemBind;

/**
 * An {@link OnItemBind} that selects item views by delegating to each item. Items must implement
 * {@link ItemBindingModel}.
 */
public class OnItemBindModel<T extends ItemBindingModel> implements OnItemBind<T> {

    @Override
    public void onItemBind(@NonNull ItemBinding itemBinding, int position, T item) {
        item.onItemBind(itemBinding);
    }
}
