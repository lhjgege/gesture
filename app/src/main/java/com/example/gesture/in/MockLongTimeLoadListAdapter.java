package com.example.gesture.in;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.example.gesture.R;
import com.example.gesture.in.adapter.BaseRecyclerAdapter;
import com.example.gesture.in.adapter.RecyclerViewHolder;
import com.example.gesture.in.entity.NewInfo;

public class MockLongTimeLoadListAdapter extends BaseRecyclerAdapter<NewInfo> {

    @Override
    public int getItemLayoutId(int viewType) {
        return R.layout.adapter_news_card_view_list_item;
    }

    @Override
    public void bindData(@NonNull RecyclerViewHolder holder, int position, NewInfo model) {
        if (model != null) {
            holder.text(R.id.tv_user_name, model.getUserName());
        }
    }

    /**
     * 这里是加载view的地方, 使用mockLongTimeLoad进行mock
     */
    @Override
    protected View inflateView(@NonNull ViewGroup parent, int layoutId) {
        return LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
    }

}
