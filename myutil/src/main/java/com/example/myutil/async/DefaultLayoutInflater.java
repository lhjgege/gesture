package com.example.myutil.async;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;


public class DefaultLayoutInflater implements PreInflateHelper.ILayoutInflater {

    private AsyncLayoutLoader mInflater;

    private DefaultLayoutInflater() {

    }

    private static final class InstanceHolder {
        static final DefaultLayoutInflater sInstance = new DefaultLayoutInflater();
    }

    public static DefaultLayoutInflater get() {
        return InstanceHolder.sInstance;
    }

    @Override
    public void asyncInflateView(@NonNull ViewGroup parent, int layoutId, PreInflateHelper.InflateCallback callback) {
        if (mInflater == null) {
            Context context = parent.getContext();
            mInflater = AsyncLayoutLoader.getInstance(new ContextThemeWrapper(context.getApplicationContext(), context.getTheme()));
        }
        mInflater.inflate(layoutId, parent, (view, resId, parent1) -> {
            if (callback != null) {
                callback.onInflateFinished(resId, view);
            }
        });
    }

    @Override
    public View inflateView(@NonNull ViewGroup parent, int layoutId) {
        return LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
    }
}
