package com.example.myutil

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.math.abs


/**
 * 设置RecyclerView item选项每次点击都自动居中显示
 */
fun RecyclerView.toRecyclerViewScrollItemCenter(pos: Int) {
    val targetPosition: Int = pos
    this.smoothScrollToPosition(targetPosition)
    this.post {
        val targetView: View? =
            this.layoutManager?.findViewByPosition(targetPosition)
        if (targetView != null) {
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            val recyclerViewCenter: Int = this.width / 2
            val targetViewCenter = location[0] + targetView.width / 2
            val dy = targetViewCenter - recyclerViewCenter
            this.smoothScrollBy(dy, 0)
        }
    }
}

/**
 * 使用TabLayout的tab设置margin
 */
fun TabLayout.toMargin(left: Int, top: Int, right: Int, bottom: Int) {
    for (i in 0 until this.tabCount) {
        val tab: TabLayout.Tab? = this.getTabAt(i)
        if (tab != null) {
            val tabView = (this.getChildAt(0) as ViewGroup).getChildAt(i)
            if (tabView != null) {
                // 设置左右间隔
                val params = tabView.layoutParams as MarginLayoutParams
                params.setMargins(left, top, right, bottom) // 左右间隔
                tabView.layoutParams = params
            }
        }
    }
}

/**
 * 陪置TabLayout的tab设置
 * 注意使用TabLayoutMediator使用需要配置App的 theme为Theme.MaterialComponents.Light.NoActionBar.Bridge
 */
fun TabLayout.toTabLayoutMediator(
    vp: ViewPager2,
    autoRefresh: Boolean = true,
    smoothScroll: Boolean = true,
    callback: (tab: TabLayout.Tab, pos: Int) -> Unit
) {
    TabLayoutMediator(this, vp, autoRefresh, smoothScroll) { tab, pos ->
        callback.invoke(tab, pos)
    }.attach()
}

/**
 * 设置包含EditText父布局在软键盘弹出自动在软键盘上面
 */
private val mKeyboardViewPadding = mutableMapOf<String, Int>()
fun View.toEditToKeyBoardBottom(act: AppCompatActivity) {
    val onb = OnGlobalLayoutListener {
        val heightDiff: Int = getDecorViewInvisibleHeight(act.window)
        if (heightDiff > 0) {
            this@toEditToKeyBoardBottom.setPadding(
                mKeyboardViewPadding["left"] ?: 0,
                mKeyboardViewPadding["top"] ?: 0,
                mKeyboardViewPadding["right"] ?: 0,
                (mKeyboardViewPadding["bottom"] ?: 0).plus(
                    if (getStatusBarHeight() > 0) heightDiff.minus(
                        getStatusBarHeight()
                    ) else getStatusBarHeight()
                )
            )
        } else {
            this@toEditToKeyBoardBottom.setPadding(
                mKeyboardViewPadding["left"] ?: 0,
                mKeyboardViewPadding["top"] ?: 0,
                mKeyboardViewPadding["right"] ?: 0,
                mKeyboardViewPadding["bottom"] ?: 0
            )
        }
    }

    mKeyboardViewPadding["top"] = this.paddingTop
    mKeyboardViewPadding["left"] = this.paddingLeft
    mKeyboardViewPadding["right"] = this.paddingRight
    mKeyboardViewPadding["bottom"] = this.paddingBottom
    this@toEditToKeyBoardBottom.viewTreeObserver.addOnGlobalLayoutListener(onb)
    act.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            mKeyboardViewPadding.clear()
            this@toEditToKeyBoardBottom.viewTreeObserver.removeOnGlobalLayoutListener(onb)
        }
    })
}

/**
 * 获取软键盘的高度
 *
 * @return 软键盘高度
 */
private fun getDecorViewInvisibleHeight(window: Window): Int {
    val decorView = window.decorView
    val outRect = Rect()
    var sDecorViewDelta = 0
    decorView.getWindowVisibleDisplayFrame(outRect)
    Log.d(
        "KeyboardUtils",
        "getDecorViewInvisibleHeight: " + (decorView.bottom - outRect.bottom)
    )
    val delta = abs((decorView.bottom - outRect.bottom).toDouble()).toInt()
    if (delta <= getNavBarHeight() + getStatusBarHeight()) {
        sDecorViewDelta = delta
        return 0
    }
    return delta - sDecorViewDelta
}

@SuppressLint("DiscouragedApi")
fun getStatusBarHeight(): Int {
    val resources = Resources.getSystem()
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return resources.getDimensionPixelSize(resourceId)
}

@SuppressLint("DiscouragedApi")
private fun getNavBarHeight(): Int {
    val res = Resources.getSystem()
    val resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (resourceId != 0) {
        res.getDimensionPixelSize(resourceId)
    } else {
        0
    }
}

