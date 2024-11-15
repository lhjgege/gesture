package com.example.myutil

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

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


