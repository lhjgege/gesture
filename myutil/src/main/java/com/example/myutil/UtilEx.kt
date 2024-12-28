package com.example.myutil

import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
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

/**
 * 列表单选
 */
fun <T> toSingleChoice(selectList: MutableList<T>, pos: Int) {
    selectList.forEachIndexed { index, onItemSelect ->
        if (onItemSelect is OnItemSelect) {
            onItemSelect.setSelect(index == pos)
        }
    }
}

/**
 * 列表多选
 */
fun <T> toMultipleChoice(selectList: MutableList<T>, pos: Int) {
    selectList.forEachIndexed { index, onItemSelect ->
        if (pos == index) {
            if (onItemSelect is OnItemSelect) {
                onItemSelect.setSelect(!onItemSelect.getSelect())
            }
        }
    }
}

/**
 * setAllCorners RoundedCornerTreatment 设置圆角
 * setAllCornerSizes 设置圆角大小
 * setBottomEdge 设置底部外部形状
 * TriangleEdgeTreatment(20f, false) 设置外三角 注意设置外三角时候记得设置父类属性 clipChildren=false 并且内部View不能占满父类空间留一点空间，否则不会显示外三角
 */

fun toMaterialShapeDrawable(
    materialShapeDrawable: (MaterialShapeDrawable) -> Unit = {},
    builder: (ShapeAppearanceModel.Builder) -> Unit = {},
    paintStyle: Paint.Style = Paint.Style.FILL,
    @ColorInt bg: Int = Color.parseColor("#000000")
): MaterialShapeDrawable {
    val shapeAppearanceModel = ShapeAppearanceModel.builder()
    builder.invoke(shapeAppearanceModel)
    return MaterialShapeDrawable(shapeAppearanceModel.build()).apply {
        setTint(bg)
        this@apply.paintStyle = paintStyle
        materialShapeDrawable.invoke(this)
    }
}

