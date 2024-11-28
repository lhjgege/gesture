package com.example.gesture.in.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.Collection;

/**
 * 通用的RecyclerView适配器
 *
 * @author XUE
 * @date 2017/9/10 18:30
 */
public abstract class BaseRecyclerAdapter<T> extends XRecyclerAdapter<T, RecyclerViewHolder> {

    public BaseRecyclerAdapter() {
        super();
    }

    public BaseRecyclerAdapter(Collection<T> list) {
        super(list);
    }

    public BaseRecyclerAdapter(T[] data) {
        super(data);
    }

    /**
     * 适配的布局
     *
     * @param viewType view类型
     * @return view类型对应的布局
     */
    protected abstract int getItemLayoutId(int viewType);

    @NonNull
    @Override
    protected RecyclerViewHolder getViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecyclerViewHolder(inflateView(parent, getItemLayoutId(viewType)));
    }

}
