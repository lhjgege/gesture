package com.lhj.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.OnTouchListener
import android.view.ViewParent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ImageView.ScaleType
import android.widget.OverScroller
import com.lhj.gesture.Compat.postOnAnimation
import com.lhj.gesture.GestureUtil.checkZoomLevels
import com.lhj.gesture.GestureUtil.isSupportedScaleType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class GestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) :
    FrameLayout(context, attrs, defStyleAttr, defStyleRes), OnTouchListener,
    OnLayoutChangeListener {
    private val mBaseMatrix: Matrix = Matrix()
    val imageMatrix: Matrix = Matrix()
    private val mSuppMatrix: Matrix = Matrix()
    private val mDisplayRect: RectF = RectF()
    private val mMatrixValues: FloatArray = FloatArray(9)
    private var mInterpolator: Interpolator = AccelerateDecelerateInterpolator()
    private var mZoomDuration: Int = DEFAULT_ZOOM_DURATION
    private var mMinScale: Float = DEFAULT_MIN_SCALE
    private var mMidScale: Float = DEFAULT_MID_SCALE
    private var mMaxScale: Float = DEFAULT_MAX_SCALE
    private var mAllowParentInterceptOnEdge: Boolean = true
    private var mBlockParentIntercept: Boolean = false
    private var mGestureDetector: GestureDetector? = null
    private var mScaleDragDetector: CustomGestureDetector? = null
    private var mMatrixChangeListener: OnMatrixChangedListener? = null
    private var mPhotoTapListener: OnPhotoTapListener? = null
    private var mOutsidePhotoTapListener: OnOutsidePhotoTapListener? = null
    private var mViewTapListener: OnViewTapListener? = null
    private var mOnClickListener: OnClickListener? = null
    private var mLongClickListener: OnLongClickListener? = null
    private var mScaleChangeListener: OnScaleChangedListener? = null
    private var mSingleFlingListener: OnSingleFlingListener? = null
    private var mOnViewDragListener: OnViewDragListener? = null
    private var mChiladView: View? = null
    private var mCurrentFlingRunnable: FlingRunnable? = null
    private var mHorizontalScrollEdge: Int = HORIZONTAL_EDGE_BOTH
    private var mVerticalScrollEdge: Int = VERTICAL_EDGE_BOTH
    private var mBaseRotation: Float = 0f

    @get:Deprecated("")
    var isZoomEnabled: Boolean = true
        private set
    private var mScaleType: ScaleType = ScaleType.FIT_CENTER

    override fun addView(child: View) {
        check(childCount < 1) { "GestureView can host only one child." }
        super.addView(child)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        init()
    }

    private val chiladView: View?
        get() {
            if (childCount == 1) {
                return getChildAt(0)
            }
            return null
        }

    private val onGestureListener: OnGestureListener = object : OnGestureListener {
        override fun onDrag(dx: Float, dy: Float) {
            if (mScaleDragDetector!!.isScaling) {
                return
            }
            if (mOnViewDragListener != null) {
                mOnViewDragListener!!.onDrag(dx, dy)
            }
            mSuppMatrix.postTranslate(dx, dy)
            checkAndDisplayMatrix()

            val parent: ViewParent? = parent
            if (mAllowParentInterceptOnEdge && !mScaleDragDetector!!.isScaling && !mBlockParentIntercept) {
                if (mHorizontalScrollEdge == HORIZONTAL_EDGE_BOTH || (mHorizontalScrollEdge == HORIZONTAL_EDGE_LEFT
                            && dx >= 1f) || (mHorizontalScrollEdge == HORIZONTAL_EDGE_RIGHT && dx <= -1f) || (mVerticalScrollEdge == VERTICAL_EDGE_TOP
                            && dy >= 1f) || (mVerticalScrollEdge == VERTICAL_EDGE_BOTTOM && dy <= -1f)
                ) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            } else {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }

        override fun onFling(startX: Float, startY: Float, velocityX: Float, velocityY: Float) {
            mCurrentFlingRunnable = FlingRunnable(mChiladView!!.context)
            mCurrentFlingRunnable!!.fling(
                getImageViewWidth(mChiladView!!), getImageViewHeight(
                    mChiladView!!
                ), velocityX.toInt(),
                velocityY.toInt()
            )
            mChiladView!!.post(mCurrentFlingRunnable)
        }

        override fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
            if (this@GestureView.scale < mMaxScale || scaleFactor < 1f) {
                if (mScaleChangeListener != null) {
                    mScaleChangeListener!!.onScaleChange(scaleFactor, focusX, focusY)
                }
                mSuppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
                checkAndDisplayMatrix()
            }
        }
    }

    private fun init() {
        mChiladView = chiladView
        if (mChiladView == null) {
            return
        }
        mChiladView!!.setOnTouchListener(this)
        mChiladView!!.addOnLayoutChangeListener(this)
        if (mChiladView!!.isInEditMode) {
            return
        }
        mBaseRotation = 0.0f
        mScaleDragDetector = CustomGestureDetector(mChiladView!!.context, onGestureListener)
        mGestureDetector =
            GestureDetector(mChiladView!!.context, object : SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (mLongClickListener != null) {
                        mLongClickListener!!.onLongClick(mChiladView)
                    }
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (mSingleFlingListener != null) {
                        if (this@GestureView.scale > DEFAULT_MIN_SCALE) {
                            return false
                        }
                        if (e1!!.pointerCount > SINGLE_TOUCH || e2.pointerCount > SINGLE_TOUCH) {
                            return false
                        }
                        return mSingleFlingListener!!.onFling(e1, e2, velocityX, velocityY)
                    }
                    return false
                }
            })
        mGestureDetector!!.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (mOnClickListener != null) {
                    mOnClickListener!!.onClick(mChiladView)
                }
                val displayRect: RectF? = this@GestureView.displayRect
                val x: Float = e.x
                val y: Float = e.y
                if (mViewTapListener != null) {
                    mViewTapListener!!.onViewTap(mChiladView, x, y)
                }
                if (displayRect != null) {
                    if (displayRect.contains(x, y)) {
                        val xResult: Float = (x - displayRect.left) / displayRect.width()
                        val yResult: Float = (y - displayRect.top) / displayRect.height()
                        if (mPhotoTapListener != null) {
                            mPhotoTapListener!!.onPhotoTap(mChiladView, xResult, yResult)
                        }
                        return true
                    } else {
                        if (mOutsidePhotoTapListener != null) {
                            mOutsidePhotoTapListener!!.onOutsidePhotoTap(mChiladView)
                        }
                    }
                }
                return false
            }

            override fun onDoubleTap(ev: MotionEvent): Boolean {
                try {
                    val scale: Float = this@GestureView.scale
                    val x: Float = ev.x
                    val y: Float = ev.y
                    if (scale < this@GestureView.mediumScale) {
                        setScale(this@GestureView.mediumScale, x, y, true)
                    } else if (scale >= this@GestureView.mediumScale && scale < this@GestureView.maximumScale) {
                        setScale(this@GestureView.maximumScale, x, y, true)
                    } else {
                        setScale(this@GestureView.minimumScale, x, y, true)
                    }
                } catch (ignored: ArrayIndexOutOfBoundsException) {
                }
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                return false
            }
        })
    }

    fun setOnDoubleTapListener(newOnDoubleTapListener: GestureDetector.OnDoubleTapListener?) {
        mGestureDetector!!.setOnDoubleTapListener(newOnDoubleTapListener)
    }

    fun setOnScaleChangeListener(onScaleChangeListener: OnScaleChangedListener?) {
        this.mScaleChangeListener = onScaleChangeListener
    }

    fun setOnSingleFlingListener(onSingleFlingListener: OnSingleFlingListener?) {
        this.mSingleFlingListener = onSingleFlingListener
    }

    val displayRect: RectF?
        get() {
            checkMatrixBounds()
            return getDisplayRect(drawMatrix)
        }

    fun setDisplayMatrix(finalMatrix: Matrix): Boolean {
        requireNotNull(finalMatrix) { "Matrix cannot be null" }
        if (mChiladView == null) {
            return false
        }
        mSuppMatrix.set(finalMatrix)
        checkAndDisplayMatrix()
        return true
    }

    fun setBaseRotation(degrees: Float) {
        mBaseRotation = degrees % 360
        update()
        setRotationBy(mBaseRotation)
        checkAndDisplayMatrix()
    }

    fun setRotationTo(degrees: Float) {
        mSuppMatrix.setRotate(degrees % 360)
        checkAndDisplayMatrix()
    }

    fun setRotationBy(degrees: Float) {
        mSuppMatrix.postRotate(degrees % 360)
        checkAndDisplayMatrix()
    }

    var minimumScale: Float
        get() = mMinScale
        set(minimumScale) {
            checkZoomLevels(minimumScale, mMidScale, mMaxScale)
            mMinScale = minimumScale
        }

    var mediumScale: Float
        get() = mMidScale
        set(mediumScale) {
            checkZoomLevels(mMinScale, mediumScale, mMaxScale)
            mMidScale = mediumScale
        }

    var maximumScale: Float
        get() = mMaxScale
        set(maximumScale) {
            checkZoomLevels(mMinScale, mMidScale, maximumScale)
            mMaxScale = maximumScale
        }

    var scale: Float
        get() = sqrt(
            (getValue(mSuppMatrix, Matrix.MSCALE_X).pow(2) + getValue(
                mSuppMatrix,
                Matrix.MSKEW_Y
            ).pow(2))
        )
        set(scale) {
            setScale(scale, false)
        }

    var scaleType: ScaleType
        get() {
            return mScaleType
        }
        set(scaleType) {
            if (isSupportedScaleType(scaleType) && scaleType != mScaleType) {
                mScaleType = scaleType
                update()
            }
        }

    override fun onLayoutChange(
        v: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix(mChiladView)
        }
    }

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        var handled: Boolean = false
        if (isZoomEnabled) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    val parent: ViewParent? = v.parent

                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }

                    cancelFling()
                }

                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> if (scale < mMinScale) {
                    val rect: RectF? = displayRect
                    if (rect != null) {
                        v.post(
                            AnimatedZoomRunnable(
                                scale,
                                mMinScale,
                                rect.centerX(),
                                rect.centerY()
                            )
                        )
                        handled = true
                    }
                } else if (scale > mMaxScale) {
                    val rect: RectF? = displayRect
                    if (rect != null) {
                        v.post(
                            AnimatedZoomRunnable(
                                scale,
                                mMaxScale,
                                rect.centerX(),
                                rect.centerY()
                            )
                        )
                        handled = true
                    }
                }
            }
            if (mScaleDragDetector != null) {
                val wasScaling: Boolean = mScaleDragDetector!!.isScaling
                val wasDragging: Boolean = mScaleDragDetector!!.isDragging
                handled = mScaleDragDetector!!.onTouchEvent(ev)
                val didntScale: Boolean = !wasScaling && !mScaleDragDetector!!.isScaling
                val didntDrag: Boolean = !wasDragging && !mScaleDragDetector!!.isDragging
                mBlockParentIntercept = didntScale && didntDrag
            }
            if (mGestureDetector != null && mGestureDetector!!.onTouchEvent(ev)) {
                handled = true
            }
        }
        return handled
    }

    fun setAllowParentInterceptOnEdge(allow: Boolean) {
        mAllowParentInterceptOnEdge = allow
    }

    fun setScaleLevels(minimumScale: Float, mediumScale: Float, maximumScale: Float) {
        checkZoomLevels(minimumScale, mediumScale, maximumScale)
        mMinScale = minimumScale
        mMidScale = mediumScale
        mMaxScale = maximumScale
    }

    override fun setOnLongClickListener(listener: OnLongClickListener?) {
        mLongClickListener = listener
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        mOnClickListener = listener
    }

    fun setOnMatrixChangeListener(listener: OnMatrixChangedListener?) {
        mMatrixChangeListener = listener
    }

    fun setOnPhotoTapListener(listener: OnPhotoTapListener?) {
        mPhotoTapListener = listener
    }

    fun setOnOutsidePhotoTapListener(mOutsidePhotoTapListener: OnOutsidePhotoTapListener?) {
        this.mOutsidePhotoTapListener = mOutsidePhotoTapListener
    }

    fun setOnViewTapListener(listener: OnViewTapListener?) {
        mViewTapListener = listener
    }

    fun setOnViewDragListener(listener: OnViewDragListener?) {
        mOnViewDragListener = listener
    }

    fun setScale(scale: Float, animate: Boolean) {
        setScale(
            scale,
            (mChiladView!!.right).toFloat() / 2,
            (mChiladView!!.bottom).toFloat() / 2,
            animate
        )
    }

    fun setScale(scale: Float, focalX: Float, focalY: Float, animate: Boolean) {
        // Check to see if the scale is within bounds
        require(!(scale < mMinScale || scale > mMaxScale)) { "Scale must be within the range of minScale and maxScale" }
        if (animate) {
            mChiladView!!.post(AnimatedZoomRunnable(this.scale, scale, focalX, focalY))
        } else {
            mSuppMatrix.setScale(scale, scale, focalX, focalY)
            checkAndDisplayMatrix()
        }
    }


    fun setZoomInterpolator(interpolator: Interpolator) {
        mInterpolator = interpolator
    }

    var isZoomable: Boolean
        get() {
            return isZoomEnabled
        }
        set(zoomable) {
            isZoomEnabled = zoomable
            update()
        }

    fun update() {
        if (isZoomEnabled) {
            // Update the base matrix using the current drawable
            updateBaseMatrix(mChiladView)
        } else {
            // Reset the Matrix...
            resetMatrix()
        }
    }


    fun getDisplayMatrix(matrix: Matrix) {
        matrix.set(drawMatrix)
    }


    fun getSuppMatrix(matrix: Matrix) {
        matrix.set(mSuppMatrix)
    }

    private val drawMatrix: Matrix
        get() {
            imageMatrix.set(mBaseMatrix)
            imageMatrix.postConcat(mSuppMatrix)
            return imageMatrix
        }

    fun setZoomTransitionDuration(milliseconds: Int) {
        this.mZoomDuration = milliseconds
    }

    private fun getValue(matrix: Matrix, whichValue: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[whichValue]
    }


    private fun resetMatrix() {
        mSuppMatrix.reset()
        setRotationBy(mBaseRotation)
        setImageViewMatrix(drawMatrix)
        checkMatrixBounds()
    }

    @SuppressLint("NewApi")
    private fun setImageViewMatrix(matrix: Matrix) {
        mChiladView!!.animationMatrix = matrix
        // Call MatrixChangedListener if needed
        if (mMatrixChangeListener != null) {
            val displayRect: RectF? = getDisplayRect(matrix)
            if (displayRect != null) {
                mMatrixChangeListener!!.onMatrixChanged(displayRect)
            }
        }
    }


    private fun checkAndDisplayMatrix() {
        if (checkMatrixBounds()) {
            setImageViewMatrix(drawMatrix)
        }
    }


    private fun getDisplayRect(matrix: Matrix): RectF? {
        if (mChiladView != null) {
            mDisplayRect.set(0f, 0f, mChiladView!!.width.toFloat(), mChiladView!!.height.toFloat())
            matrix.mapRect(mDisplayRect)
            return mDisplayRect
        }
        return null
    }

    private fun updateBaseMatrix(view: View?) {
        if (view == null) {
            return
        }
        val viewWidth: Float = getImageViewWidth(mChiladView!!).toFloat()
        val viewHeight: Float = getImageViewHeight(mChiladView!!).toFloat()
        val drawableWidth: Int = getImageViewWidth(mChiladView!!)
        val drawableHeight: Int = getImageViewHeight(mChiladView!!)
        mBaseMatrix.reset()
        val widthScale: Float = viewWidth / drawableWidth
        val heightScale: Float = viewHeight / drawableHeight
        if (mScaleType == ScaleType.CENTER) {
            mBaseMatrix.postTranslate(
                (viewWidth - drawableWidth) / 2f,
                (viewHeight - drawableHeight) / 2f
            )
        } else if (mScaleType == ScaleType.CENTER_CROP) {
            val scale: Float =
                max(widthScale.toDouble(), heightScale.toDouble()).toFloat()
            mBaseMatrix.postScale(scale, scale)
            mBaseMatrix.postTranslate(
                (viewWidth - drawableWidth * scale) / 2f,
                (viewHeight - drawableHeight * scale) / 2f
            )
        } else if (mScaleType == ScaleType.CENTER_INSIDE) {
            val scale: Float =
                min(1.0, min(widthScale.toDouble(), heightScale.toDouble()))
                    .toFloat()
            mBaseMatrix.postScale(scale, scale)
            mBaseMatrix.postTranslate(
                (viewWidth - drawableWidth * scale) / 2f,
                (viewHeight - drawableHeight * scale) / 2f
            )
        } else {
            var mTempSrc: RectF = RectF(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat())
            val mTempDst: RectF = RectF(0f, 0f, viewWidth, viewHeight)
            if (mBaseRotation.toInt() % 180 != 0) {
                mTempSrc = RectF(0f, 0f, drawableHeight.toFloat(), drawableWidth.toFloat())
            }
            when (mScaleType) {
                ScaleType.FIT_CENTER -> mBaseMatrix.setRectToRect(
                    mTempSrc,
                    mTempDst,
                    Matrix.ScaleToFit.CENTER
                )

                ScaleType.FIT_START -> mBaseMatrix.setRectToRect(
                    mTempSrc,
                    mTempDst,
                    Matrix.ScaleToFit.START
                )

                ScaleType.FIT_END -> mBaseMatrix.setRectToRect(
                    mTempSrc,
                    mTempDst,
                    Matrix.ScaleToFit.END
                )

                ScaleType.FIT_XY -> mBaseMatrix.setRectToRect(
                    mTempSrc,
                    mTempDst,
                    Matrix.ScaleToFit.FILL
                )

                else -> {}
            }
        }
        resetMatrix()
    }

    private fun checkMatrixBounds(): Boolean {
        val rect: RectF? = getDisplayRect(drawMatrix)
        if (rect == null) {
            return false
        }
        val height: Float = rect.height()
        val width: Float = rect.width()
        var deltaX: Float = 0f
        var deltaY: Float = 0f
        val viewHeight: Int = getImageViewHeight(mChiladView!!)
        if (height <= viewHeight) {
            when (mScaleType) {
                ScaleType.FIT_START -> deltaY = -rect.top
                ScaleType.FIT_END -> deltaY = viewHeight - height - rect.top
                else -> deltaY = (viewHeight - height) / 2 - rect.top
            }
            mVerticalScrollEdge = VERTICAL_EDGE_BOTH
        } else if (rect.top > 0) {
            mVerticalScrollEdge = VERTICAL_EDGE_TOP
            deltaY = -rect.top
        } else if (rect.bottom < viewHeight) {
            mVerticalScrollEdge = VERTICAL_EDGE_BOTTOM
            deltaY = viewHeight - rect.bottom
        } else {
            mVerticalScrollEdge = VERTICAL_EDGE_NONE
        }
        val viewWidth: Int = getImageViewWidth(mChiladView!!)
        if (width <= viewWidth) {
            when (mScaleType) {
                ScaleType.FIT_START -> deltaX = -rect.left
                ScaleType.FIT_END -> deltaX = viewWidth - width - rect.left
                else -> deltaX = (viewWidth - width) / 2 - rect.left
            }
            mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH
        } else if (rect.left > 0) {
            mHorizontalScrollEdge = HORIZONTAL_EDGE_LEFT
            deltaX = -rect.left
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right
            mHorizontalScrollEdge = HORIZONTAL_EDGE_RIGHT
        } else {
            mHorizontalScrollEdge = HORIZONTAL_EDGE_NONE
        }
        mSuppMatrix.postTranslate(deltaX, deltaY)
        return true
    }

    private fun getImageViewWidth(view: View): Int {
        return view.width - view.paddingLeft - view.paddingRight
    }

    private fun getImageViewHeight(view: View): Int {
        return view.height - view.paddingTop - view.paddingBottom
    }

    private fun cancelFling() {
        if (mCurrentFlingRunnable != null) {
            mCurrentFlingRunnable!!.cancelFling()
            mCurrentFlingRunnable = null
        }
    }

    private inner class AnimatedZoomRunnable(
        private val mZoomStart: Float, private val mZoomEnd: Float, private val mFocalX: Float,
        private val mFocalY: Float
    ) : Runnable {
        private val mStartTime: Long

        init {
            mStartTime = System.currentTimeMillis()
        }

        override fun run() {
            val t: Float = interpolate()
            val scale: Float = mZoomStart + t * (mZoomEnd - mZoomStart)
            val deltaScale: Float = scale / this@GestureView.scale
            onGestureListener.onScale(deltaScale, mFocalX, mFocalY)
            if (t < 1f) {
                postOnAnimation(mChiladView!!, this)
            }
        }

        fun interpolate(): Float {
            var t: Float = 1f * (System.currentTimeMillis() - mStartTime) / mZoomDuration
            t = min(1.0, t.toDouble()).toFloat()
            t = mInterpolator.getInterpolation(t)
            return t
        }
    }

    private inner class FlingRunnable(context: Context?) : Runnable {
        private val mScroller: OverScroller
        private var mCurrentX: Int = 0
        private var mCurrentY: Int = 0

        init {
            mScroller = OverScroller(context)
        }

        fun cancelFling() {
            mScroller.forceFinished(true)
        }

        fun fling(viewWidth: Int, viewHeight: Int, velocityX: Int, velocityY: Int) {
            val rect: RectF? = this@GestureView.displayRect
            if (rect == null) {
                return
            }
            val startX: Int = Math.round(-rect.left)
            val minX: Int
            val maxX: Int
            val minY: Int
            val maxY: Int
            if (viewWidth < rect.width()) {
                minX = 0
                maxX = Math.round(rect.width() - viewWidth)
            } else {
                maxX = startX
                minX = maxX
            }
            val startY: Int = Math.round(-rect.top)
            if (viewHeight < rect.height()) {
                minY = 0
                maxY = Math.round(rect.height() - viewHeight)
            } else {
                maxY = startY
                minY = maxY
            }
            mCurrentX = startX
            mCurrentY = startY
            if (startX != maxX || startY != maxY) {
                mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, 0, 0)
            }
        }

        override fun run() {
            if (mScroller.isFinished) {
                return  // remaining post that should not be handled
            }
            if (mScroller.computeScrollOffset()) {
                val newX: Int = mScroller.currX
                val newY: Int = mScroller.currY
                mSuppMatrix.postTranslate(
                    (mCurrentX - newX).toFloat(),
                    (mCurrentY - newY).toFloat()
                )
                checkAndDisplayMatrix()
                mCurrentX = newX
                mCurrentY = newY
                postOnAnimation(mChiladView!!, this)
            }
        }
    }

    companion object {
        private val HORIZONTAL_EDGE_NONE: Int = -1
        private const val HORIZONTAL_EDGE_LEFT: Int = 0
        private const val HORIZONTAL_EDGE_RIGHT: Int = 1
        private const val HORIZONTAL_EDGE_BOTH: Int = 2
        private val VERTICAL_EDGE_NONE: Int = -1
        private const val VERTICAL_EDGE_TOP: Int = 0
        private const val VERTICAL_EDGE_BOTTOM: Int = 1
        private const val VERTICAL_EDGE_BOTH: Int = 2
        private const val DEFAULT_MAX_SCALE: Float = 3.0f
        private const val DEFAULT_MID_SCALE: Float = 1.75f
        private const val DEFAULT_MIN_SCALE: Float = 1.0f
        private const val DEFAULT_ZOOM_DURATION: Int = 200
        private const val SINGLE_TOUCH: Int = 1
    }
}
