package com.example.myutil.async;

import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * 在获取inflateView的地方调用 PreInflateHelper.getView(parent, layoutId)
 * Adapter构造函数中调用PreInflateHelper.preload(recyclerView, layoutId)
 */
public class PreInflateHelper {

    private static final String TAG = "PreInflateHelper";

    /**
     * 默认的预加载缓存池大小，默认是5，可根据需求设置
     */
    public static final int DEFAULT_PRELOAD_COUNT = 5;

    private final ViewCache mViewCache = new ViewCache();

    private ILayoutInflater mLayoutInflater = DefaultLayoutInflater.get();

    public void preloadOnce(@NonNull ViewGroup parent, int layoutId) {
        preloadOnce(parent, layoutId, DEFAULT_PRELOAD_COUNT);
    }

    public void preloadOnce(@NonNull ViewGroup parent, int layoutId, int maxCount) {
        preload(parent, layoutId, maxCount, 1);
    }

    public void preload(@NonNull ViewGroup parent, int layoutId) {
        preload(parent, layoutId, DEFAULT_PRELOAD_COUNT, 0);
    }

    public void preload(@NonNull ViewGroup parent, int layoutId, int maxCount) {
        preload(parent, layoutId, maxCount, 0);
    }

    public void preload(@NonNull ViewGroup parent, int layoutId, int maxCount, int forcePreCount) {
        int viewsAvailableCount = mViewCache.getViewPoolAvailableCount(layoutId);
        if (viewsAvailableCount >= maxCount) {
            return;
        }
        int needPreloadCount = maxCount - viewsAvailableCount;
        if (forcePreCount > 0) {
            needPreloadCount = Math.min(forcePreCount, needPreloadCount);
        }
        Log.d(TAG, "needPreloadCount:" + needPreloadCount + ", viewsAvailableCount:" + viewsAvailableCount);
        for (int i = 0; i < needPreloadCount; i++) {
            // 异步加载View
            preAsyncInflateView(parent, layoutId);
        }
    }

    private void preAsyncInflateView(@NonNull ViewGroup parent, int layoutId) {
        mLayoutInflater.asyncInflateView(parent, layoutId, new InflateCallback() {
            @Override
            public void onInflateFinished(int layoutId, View view) {
                mViewCache.putView(layoutId, view);
                Log.d(TAG, "mViewCache + 1, viewsAvailableCount:" + mViewCache.getViewPoolAvailableCount(layoutId));
            }
        });
    }

    public View getView(@NonNull ViewGroup parent, int layoutId) {
        return getView(parent, layoutId, DEFAULT_PRELOAD_COUNT);
    }

    public View getView(@NonNull ViewGroup parent, int layoutId, int maxCount) {
        View view = mViewCache.getView(layoutId);
        if (view != null) {
            Log.d(TAG, "get view from cache!");
            preloadOnce(parent, layoutId, maxCount);
            return view;
        }
        return mLayoutInflater.inflateView(parent, layoutId);
    }

    public PreInflateHelper setAsyncInflater(ILayoutInflater asyncInflater) {
        mLayoutInflater = asyncInflater;
        return this;
    }

    private static class ViewCache {

        private final SparseArray<LinkedList<SoftReference<View>>> mViewPools = new SparseArray<>();

        @NonNull
        public LinkedList<SoftReference<View>> getViewPool(int layoutId) {
            LinkedList<SoftReference<View>> views = mViewPools.get(layoutId);
            if (views == null) {
                views = new LinkedList<>();
                mViewPools.put(layoutId, views);
            }
            return views;
        }

        public int getViewPoolAvailableCount(int layoutId) {
            LinkedList<SoftReference<View>> views = getViewPool(layoutId);
            Iterator<SoftReference<View>> it = views.iterator();
            int count = 0;
            while (it.hasNext()) {
                if (it.next().get() != null) {
                    count++;
                } else {
                    it.remove();
                }
            }
            return count;
        }

        public void putView(int layoutId, View view) {
            if (view == null) {
                return;
            }
            getViewPool(layoutId).offer(new SoftReference<>(view));
        }

        @Nullable
        public View getView(int layoutId) {
            return getViewFromPool(getViewPool(layoutId));
        }

        private View getViewFromPool(@NonNull LinkedList<SoftReference<View>> views) {
            if (views.isEmpty()) {
                return null;
            }
            View target = views.pop().get();
            if (target == null) {
                return getViewFromPool(views);
            }
            return target;
        }
    }

    public interface ILayoutInflater {

        /**
         * 异步加载View
         *
         * @param parent   父布局
         * @param layoutId 布局资源id
         * @param callback 加载回调
         */
        void asyncInflateView(@NonNull ViewGroup parent, int layoutId, InflateCallback callback);

        /**
         * 同步加载View
         *
         * @param parent   父布局
         * @param layoutId 布局资源id
         * @return 加载的View
         */
        View inflateView(@NonNull ViewGroup parent, int layoutId);

    }


    public interface InflateCallback {

        void onInflateFinished(int layoutId, View view);
    }


}