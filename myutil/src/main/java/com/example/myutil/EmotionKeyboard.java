package com.example.myutil;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * 切换表情键盘和软键盘
 */
public class EmotionKeyboard {
    private static final String SHARE_PREFERENCE_NAME = "EmotionKeyboard"; // 存储表情键盘相关信息的SharedPreferences名称
    private static final String SHARE_PREFERENCE_SOFT_INPUT_HEIGHT = "soft_input_height"; // 软键盘高度在SharedPreferences中的key
    private Activity mActivity; // 当前Activity
    private InputMethodManager mInputManager; // 软键盘管理类
    private SharedPreferences sp; // SharedPreferences对象，用于存储软键盘高度等信息
    private View mEmotionLayout, mMenuLayout, mKeyboardViewToTop; // 表情布局,菜单布局,包含在EditView的View
    private View emotionButton, menuButton; // 表表情按钮,菜单按钮
    private String TAG = "tag";
    private EditText mEditText; // 编辑框
    private View mContentView; // 内容布局，用于固定bar的高度，防止跳闪
    private HashMap<String, Integer> mKeyboardViewPadding = new HashMap<>();

    // 构造函数私有化，只能通过with方法获取实例
    private EmotionKeyboard() {
        mKeyboardViewPadding.put("left", 0);
        mKeyboardViewPadding.put("right", 0);
        mKeyboardViewPadding.put("top", 0);
        mKeyboardViewPadding.put("bottom", 0);
    }

    /**
     * 外部静态调用，用于获取EmotionKeyboard实例
     *
     * @param activity 当前Activity
     * @return EmotionKeyboard实例
     */
    public static EmotionKeyboard with(Activity activity) {
        EmotionKeyboard emotionInputDetector = new EmotionKeyboard();
        emotionInputDetector.mActivity = activity;
        emotionInputDetector.mInputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        emotionInputDetector.sp = activity.getSharedPreferences(SHARE_PREFERENCE_NAME, Context.MODE_PRIVATE);
        return emotionInputDetector;
    }

    /**
     * 绑定内容view，此view用于固定bar的高度，防止跳闪
     *
     * @param contentView 内容view
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard bindToContent(View contentView) {
        mContentView = contentView;
        return this;
    }

    /**
     * 绑定编辑框
     *
     * @param editText 编辑框
     * @return 当前EmotionKeyboard实例
     */
    @SuppressLint("ClickableViewAccessibility")
    public EmotionKeyboard bindToEditText(EditText editText) {
        mEditText = editText;
        mEditText.requestFocus();
        mEditText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && mEmotionLayout.isShown()) {
                lockContentHeight(); // 显示软键盘时，锁定内容高度，防止跳闪。
                hideEmotionLayout(true); // 隐藏表情布局，显示软键盘
                // 软键盘显示后，释放内容高度
                mEditText.postDelayed(() -> unlockContentHeightDelayed(), 200L);
            }

            if (event.getAction() == MotionEvent.ACTION_UP && mMenuLayout.isShown()) {
                menuButton.animate().rotationBy(-45f).setDuration(250).start();
                lockContentHeight(); // 显示软键盘时，锁定内容高度，防止跳闪。
                hideMenuLayout(true); // 隐藏菜单布局，显示软键盘
                // 软键盘显示后，释放内容高度
                mEditText.postDelayed(() -> unlockContentHeightDelayed(), 200L);
            }
            return false;
        });
        return this;
    }

    /**
     * 绑定表情按钮
     *
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard bindToEmojiButton() {
        emotionButton.setOnClickListener(v -> {

            //如果点击表情按钮时正在显示菜单布局
            if (mMenuLayout.isShown()) {
                menuButton.animate().rotationBy(-45f).setDuration(250).start();
                lockContentHeight(); // 显示软键盘时，锁定内容高度，防止跳闪。
                hideMenuLayout(false); // 隐藏表情布局，但不显示软键盘
                showEmotionLayout();  // 显示表情布局
                unlockContentHeightDelayed(); // 表情布局显示后，释放内容高度
            }
            //如果已经显示表情布局，再次点击则切换键盘
            else if (mEmotionLayout.isShown()) {
                lockContentHeight(); // 显示软键盘时，锁定内容高度，防止跳闪。
                hideEmotionLayout(true); // 隐藏表情布局，显示软键盘
                unlockContentHeightDelayed(); // 软键盘显示后，释放内容高度
            } else {
                //如果正在显示键盘，则隐藏键盘显示表情布局
                if (isSoftInputShown()) { // 同上
                    lockContentHeight();
                    showEmotionLayout();
                    unlockContentHeightDelayed();
                } else {
                    //如果都没有显示，则直接显示表情布局

                    showEmotionLayout(); // 两者都没显示，直接显示表情布局
                }
            }
        });
        return this;
    }

    /**
     * 绑定菜单按钮
     *
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard bindToMenuButton() {
        menuButton.setOnClickListener(v -> {
            //如果点击菜单按钮时正在显示表情布局
            if (mEmotionLayout.isShown()) {
                //旋转45°延迟250毫秒
                menuButton.animate().rotationBy(45f).setDuration(250).start();
                lockContentHeight(); // 显示表情面板时，锁定内容高度，防止跳闪。
                hideEmotionLayout(false);   // 隐藏表情布局，但不显示软键盘
                showMenuLayout();   //显示菜单布局
                unlockContentHeightDelayed(); // 菜单布局显示后，释放内容高度
            }
            //如果已经显示菜单布局，再次点击则切换键盘
            else if (mMenuLayout.isShown()) {
                menuButton.animate().rotationBy(-45f).setDuration(250).start();

                lockContentHeight(); // 显示软键盘时，锁定内容高度，防止跳闪。
                hideMenuLayout(true); // 隐藏菜单布局，显示软键盘
                unlockContentHeightDelayed(); // 软键盘显示后，释放内容高度
            } else {
                //如果正在显示键盘
                if (isSoftInputShown()) { // 同上
                    menuButton.animate().rotationBy(45f).setDuration(250).start();
                    lockContentHeight();
                    showMenuLayout();
                    unlockContentHeightDelayed();
                } else {
                    menuButton.animate().rotationBy(45f).setDuration(250).start();
                    //如果没有显示键盘也没有显示表情
                    showMenuLayout(); // 两者都没显示，直接显示菜单布局
                }
            }
        });
        return this;
    }

    /**
     * 设置按钮键
     *
     * @param emotionView 表情内容布局
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard setButtonView(View emotionView, View menuView) {
        emotionButton = emotionView;
        menuButton = menuView;
        return this;
    }

    /**
     * 设置表情内容布局
     *
     * @param emotionView 表情内容布局
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard setEmotionView(View emotionView) {
        mEmotionLayout = emotionView;
        return this;
    }

    /**
     * 设置菜单内容布局
     *
     * @param menuView 菜单内容布局
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard setMenuView(View menuView) {
        mMenuLayout = menuView;
        return this;
    }

    private static int sDecorViewDelta = 0;

    public EmotionKeyboard bindKeyboardViewToTop(View keyboard) {
        mKeyboardViewToTop = keyboard;
        mKeyboardViewPadding.put("top", mKeyboardViewToTop.getPaddingTop());
        mKeyboardViewPadding.put("left", mKeyboardViewToTop.getPaddingLeft());
        mKeyboardViewPadding.put("right", mKeyboardViewToTop.getPaddingRight());
        mKeyboardViewPadding.put("bottom", mKeyboardViewToTop.getPaddingBottom());
        View rootView;
        rootView = mActivity.findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int heightDiff = getDecorViewInvisibleHeight(mActivity.getWindow());
            if (heightDiff > 0) {
                mKeyboardViewToTop.setPadding(mKeyboardViewPadding.get("left"), mKeyboardViewPadding.get("top"), mKeyboardViewPadding.get("right"), mKeyboardViewPadding.get("bottom") + getKeyBoardHeight());
            } else {
                mKeyboardViewToTop.setPadding(mKeyboardViewPadding.get("left"), mKeyboardViewPadding.get("top"), mKeyboardViewPadding.get("right"), mKeyboardViewPadding.get("bottom"));
            }
        });
        return this;
    }


    private int getDecorViewInvisibleHeight(@NonNull final Window window) {
        final View decorView = window.getDecorView();
        final Rect outRect = new Rect();
        decorView.getWindowVisibleDisplayFrame(outRect);
        Log.d("KeyboardUtils",
                "getDecorViewInvisibleHeight: " + (decorView.getBottom() - outRect.bottom));
        int delta = Math.abs(decorView.getBottom() - outRect.bottom);
        if (delta <= getNavBarHeight() + getStatusBarHeight()) {
            sDecorViewDelta = delta;
            return 0;
        }
        return delta - sDecorViewDelta;
    }

    public int getStatusBarHeight() {
        Resources resources = Resources.getSystem();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        return resources.getDimensionPixelSize(resourceId);
    }

    private int getNavBarHeight() {
        Resources res = Resources.getSystem();
        int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId != 0) {
            return res.getDimensionPixelSize(resourceId);
        } else {
            return 0;
        }
    }

    /**
     * 构建EmotionKeyboard实例
     *
     * @return 当前EmotionKeyboard实例
     */
    public EmotionKeyboard build() {
        // 设置软键盘的模式：SOFT_INPUT_ADJUST_RESIZE 表示Activity的主窗口总是会被调整大小，从而保证软键盘显示空间。
        // 从而方便计算软键盘的高度
        mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // 隐藏软键盘
        hideSoftInput();
        return this;
    }

    /**
     * 点击返回键时先隐藏表情/菜单布局
     *
     * @return 是否拦截返回事件
     */
    public boolean interceptBackPress() {
        if (mEmotionLayout.isShown()) {
            hideEmotionLayout(false);
            return true;
        } else if (mMenuLayout.isShown()) {
            hideMenuLayout(false);
            return true;
        }
        return false;
    }

    /**
     * 显示表情布局
     */
    private void showEmotionLayout() {
        int softInputHeight = getSupportSoftInputHeight();
        // 如果获取软键盘高度失败，则使用默认值 400
        if (softInputHeight == 0) {
            softInputHeight = sp.getInt(SHARE_PREFERENCE_SOFT_INPUT_HEIGHT, 765);
        }
        Log.d(TAG, "表情布局高度：" + softInputHeight);
        // 隐藏软键盘
        hideSoftInput();
        // 将软键盘高度设置给表情布局
        mEmotionLayout.getLayoutParams().height = softInputHeight;
        Log.d(TAG, "将软键盘高度设置给表情布局" + String.valueOf(softInputHeight));
        mEmotionLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 显示菜单布局
     */
    private void showMenuLayout() {
        int softInputHeight = getSupportSoftInputHeight();
        // 如果获取软键盘高度失败，则使用默认值 400
        if (softInputHeight == 0) {
            softInputHeight = sp.getInt(SHARE_PREFERENCE_SOFT_INPUT_HEIGHT, 765);
        }
        Log.d(TAG, "菜单布局高度：" + softInputHeight);
        // 隐藏软键盘
        hideSoftInput();
        // 将软键盘高度设置给表情布局
        mMenuLayout.getLayoutParams().height = softInputHeight;
        Log.d(TAG, "将软键盘高度设置给表情布局" + String.valueOf(softInputHeight));
        mMenuLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏表情布局
     *
     * @param showSoftInput 是否显示软键盘
     */
    private void hideEmotionLayout(boolean showSoftInput) {
        if (mEmotionLayout.isShown()) {
            mEmotionLayout.setVisibility(View.GONE);
            if (showSoftInput) {
                showSoftInput();
            }
        }
    }

    /**
     * 隐藏菜单布局
     *
     * @param showSoftInput 是否显示软键盘
     */
    private void hideMenuLayout(boolean showSoftInput) {
        if (mMenuLayout.isShown()) {
            mMenuLayout.setVisibility(View.GONE);
            if (showSoftInput) {
                showSoftInput();
            }
        }
    }

    /**
     * 锁定内容高度，防止跳闪
     */
    private void lockContentHeight() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mContentView.getLayoutParams();
        params.height = mContentView.getHeight();
        Log.d(TAG, "内容高度" + params.height);
        params.weight = 0.0F;
    }

    /**
     * 释放被锁定的内容高度
     */
    private void unlockContentHeightDelayed() {
        mEditText.postDelayed(new Runnable() {
            @Override
            public void run() {
                ((LinearLayout.LayoutParams) mContentView.getLayoutParams()).weight = 1.0F;
            }
        }, 200L);
    }

    /**
     * 编辑框获取焦点，并显示软键盘
     */
    private void showSoftInput() {
        mEditText.requestFocus();
        mEditText.post(new Runnable() {
            @Override
            public void run() {
                mInputManager.showSoftInput(mEditText, 0);
            }
        });

    }

    /**
     * 隐藏软键盘
     */
    private void hideSoftInput() {
        mInputManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }

    /**
     * 是否显示软键盘
     *
     * @return 是否显示软键盘
     */
    private boolean isSoftInputShown() {
        return getSupportSoftInputHeight() != 0;
    }

    /**
     * 获取软键盘的高度
     *
     * @return 软键盘高度
     */
    private int getSupportSoftInputHeight() {
        int softInputHeight = getDecorViewInvisibleHeight(mActivity.getWindow());
        Log.d(TAG, "软键盘高度" + (softInputHeight));
        if (softInputHeight <= 0) {
            Log.d(TAG, "EmotionKeyboard--Warning: value of softInputHeight is below zero!");
        }
        if (softInputHeight > 200) {
            sp.edit().putInt(SHARE_PREFERENCE_SOFT_INPUT_HEIGHT, softInputHeight).apply();
        }

        return softInputHeight;
    }

    /**
     * 获取底部虚拟按键栏的高度
     *
     * @return 虚拟按键栏高度
     */
    private int getSoftButtonsBarHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        mActivity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        Log.d(TAG, "虚拟按键栏高度" + (realHeight - usableHeight));
        if (realHeight > usableHeight) {
            return realHeight - usableHeight;
        } else {
            return 0;
        }
    }

    /**
     * 获取软键盘高度
     *
     * @return 软键盘高度
     */
    public int getKeyBoardHeight() {
        return sp.getInt(SHARE_PREFERENCE_SOFT_INPUT_HEIGHT, 850);
    }

    private static final String NAVIGATION = "navigationBarBackground";

    /**
     * 检查设备是否具有导航栏（虚拟或物理）。
     *
     * @return 如果存在导航栏则返回 true，否则返回 false
     */
    public boolean isNavigationBarExist() {
        // 获取窗口装饰视图的根视图
        ViewGroup vp = (ViewGroup) mActivity.getWindow().getDecorView();
        // 遍历装饰视图的所有子视图
        if (vp != null) {
            for (int i = 0; i < vp.getChildCount(); i++) {
                // 获取子视图的上下文
                vp.getChildAt(i).getContext().getPackageName();

                // 检查子视图是否具有导航栏
                if (vp.getChildAt(i).getId() != View.NO_ID && NAVIGATION.equals(mActivity.getResources().getResourceEntryName(vp.getChildAt(i).getId()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
