package com.example.gesture.in;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myutil.async.PreInflateHelper;

public class OptimizeListAdapter extends MockLongTimeLoadListAdapter {
    private static final class InstanceHolder {
        static final PreInflateHelper sInstance = new PreInflateHelper();
    }

    public static PreInflateHelper getInflateHelper() {
        return OptimizeListAdapter.InstanceHolder.sInstance;
    }

    public OptimizeListAdapter(RecyclerView recyclerView) {
        getInflateHelper().preload(recyclerView, getItemLayoutId(0));
    }

    @Override
    protected View inflateView(@NonNull ViewGroup parent, int layoutId) {
        return getInflateHelper().getView(parent, layoutId);
    }
}