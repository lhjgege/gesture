package com.lhj.gesture

import android.view.MotionEvent

interface OnSingleFlingListener {
    fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean
}
