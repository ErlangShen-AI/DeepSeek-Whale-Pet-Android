package com.whalepet.pet

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.whalepet.core.Motion
import com.whalepet.core.PetGeometry
import com.whalepet.core.QuoteLine
import kotlin.math.abs

/**
 * 桌宠视图。
 *
 * 同一套绘制与动画按层拆到两个窗口：鲸鱼层只画鲸鱼并接收按压与拖拽，气泡层只画气泡并接收点击。
 * 两层共享同一份视觉状态，因此镜像、按压与气泡开合在两个窗口里完全同步。
 * 开合与切换的时长从 Motion 令牌取值。
 */
class WhaleView(
    context: Context,
    val layer: Layer,
    sharedVisual: WhaleArt.Visual? = null,
    sharedArt: WhaleArt? = null
) : View(context) {

    enum class Layer { WHALE, BUBBLE }

    interface GestureListener {
        /** 按压状态，按下、松手与取消共用这一个入口，状态相同不会重复回调。 */
        fun onPressChanged(pressed: Boolean)
        fun onStartDrag(rawX: Float, rawY: Float)
        fun onDrag(rawX: Float, rawY: Float)
        fun onEndDrag()
        fun onWhaleTap()
        fun onBubbleTap()
    }

    var listener: GestureListener? = null

    /** 视图自身有动画时通知外部开始推帧。 */
    var frameRequester: (() -> Unit)? = null

    /** 按压动画的宿主视图，气泡层收到的鲸鱼触摸交给鲸鱼层驱动，动画只有一处推帧。 */
    var pressHost: WhaleView? = null

    /** 绘制实例，同一层的多个视图共享一份位图与掩码。 */
    val art = sharedArt ?: WhaleArt(context)

    /** 两个窗口共用同一份状态，气泡层只负责显示。 */
    val visual: WhaleArt.Visual = sharedVisual ?: WhaleArt.Visual()

    private val pressX = Tween()
    private val pressY = Tween()
    private val shapeFades = List(WhaleArt.SHAPE_COUNT) { Tween() }
    private val textFade = Tween()
    private val mirrorFlip = Tween()
    private val swapFade = Tween()
    private val shapeValues = FloatArray(WhaleArt.SHAPE_COUNT)

    private var reducedMotion = false
    private var pendingLines: List<QuoteLine?>? = null
    private var pendingGif = false
    private var activeLines: List<QuoteLine?> = emptyList()
    private var activeGif = false

    /** 镜像的目标值，1 为正向、-1 为翻转；绘制的当前值由补间给出。 */
    private var mirrorTarget = 1f

    /** 目标镜像状态，翻转动画结束后与绘制状态一致。 */
    private val mirrored: Boolean
        get() = mirrorTarget < 0f
    private var target: TouchTarget = TouchTarget.NONE
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false
    private var gestureActive = false

    private var viewportLeft = 0f
    private var viewportTop = 0f

    var unit: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var bubbleShown = false
        private set

    /** 共享视觉的翻转过场是否进行中，两层读同一份值。 */
    val isMirroring: Boolean
        get() = abs(abs(visual.mirrorX) - 1f) > FLIP_EPSILON

    /** 共享视觉当前的镜像方向，命中判定与绘制读同一份值。 */
    private val visualMirrored: Boolean
        get() = visual.mirrorX < 0f

    val isMirrored: Boolean
        get() = mirrored

    /** 鲸鱼实际绘制范围，供窗口收紧使用。 */
    val whaleBounds: RectF
        get() = art.whaleBounds

    /** 动图资源是否可用，内容入口据此决定动图台词是否降级为文字。 */
    val hasGif: Boolean
        get() = art.hasGif

    init {
        pressX.jumpTo(1f)
        pressY.jumpTo(1f)
        textFade.jumpTo(0f)
        mirrorFlip.jumpTo(1f)
        swapFade.jumpTo(1f)
        shapeFades.forEach { it.jumpTo(0f) }
    }

    fun applyReducedMotion(reduced: Boolean) {
        reducedMotion = reduced
    }

    /** 设置该窗口覆盖的画布区域左上角，绘制与命中判定共用这组数值。 */
    fun setViewport(left: Float, top: Float) {
        if (viewportLeft == left && viewportTop == top) return
        viewportLeft = left
        viewportTop = top
        invalidate()
    }

    /** 吸附到左缘时整体镜像，文字在绘制时反向翻转以保持可读。 */
    fun setMirrored(mirrored: Boolean, animated: Boolean) {
        val next = if (mirrored) -1f else 1f
        if (mirrorTarget == next) return
        mirrorTarget = next
        if (!animated || reducedMotion) {
            mirrorFlip.jumpTo(next)
            visual.mirrorX = next
            invalidate()
            return
        }
        mirrorFlip.start(mirrorFlip.value, next, Motion.FLIP_SECONDS, 0f, Easing.CSS_EASE)
        requestFrame()
    }

    /**
     * 打开气泡。
     *
     * 三个图形与文字各自从当前值推向全开，重复打开只是重新指向同一目标，
     * 因此关闭动画进行到一半再打开也能连续收回。
     */
    fun showBubble() {
        bubbleShown = true
        pendingLines = null
        if (swapFade.value < 1f) swapFade.jumpTo(1f)
        for (index in shapeFades.indices) {
            val tween = shapeFades[index]
            tween.start(tween.value, 1f, Motion.SHAPE_SECONDS, openDelay(index), Easing.CSS_EASE)
        }
        if (textFade.value < 1f) {
            textFade.start(
                textFade.value,
                1f,
                Motion.BUBBLE_FADE_SECONDS,
                if (reducedMotion) 0f else Motion.TEXT_FADE_DELAY_SECONDS,
                Easing.CSS_EASE
            )
        }
        requestFrame()
    }

    fun hideBubble() {
        if (!bubbleShown) return
        bubbleShown = false
        for (index in shapeFades.indices) {
            val tween = shapeFades[index]
            tween.start(tween.value, 0f, Motion.SHAPE_SECONDS, closeDelay(index), Easing.CSS_EASE)
        }
        textFade.start(textFade.value, 0f, Motion.BUBBLE_FADE_SECONDS, 0f, Easing.CSS_EASE)
        requestFrame()
    }

    /**
     * 显示外部给出的内容。
     *
     * animated 为真时三行一起淡出、过半时替换为新内容再淡入，用于切换台词；
     * 为假时就地替换，三行内容直接生效。
     */
    fun setContent(lines: List<QuoteLine?>, gif: Boolean, animated: Boolean) {
        if (!animated || !bubbleShown) {
            pendingLines = null
            activeLines = lines
            activeGif = gif
            if (swapFade.value < 1f) swapFade.jumpTo(1f)
            requestFrame()
            return
        }
        pendingLines = lines
        pendingGif = gif
        if (swapFade.isRunning) return
        beginSwap()
    }

    fun advanceFrame(deltaSeconds: Float): Boolean {
        var active = false
        if (pressX.advance(deltaSeconds)) active = true
        if (pressY.advance(deltaSeconds)) active = true
        if (textFade.advance(deltaSeconds)) active = true
        if (mirrorFlip.advance(deltaSeconds)) active = true
        for (tween in shapeFades) {
            if (tween.advance(deltaSeconds)) active = true
        }
        if (swapFade.advance(deltaSeconds)) active = true
        if (pendingLines != null && swapFade.value <= SWAP_SWITCH_POINT) {
            val lines = pendingLines
            if (lines != null) {
                activeLines = lines
                activeGif = pendingGif
            }
            pendingLines = null
            swapFade.start(0f, 1f, Motion.SWAP_FADE_SECONDS, 0f, Easing.CSS_EASE)
            active = true
        }
        syncVisual()
        invalidate()
        return active
    }

    fun stopAnimations() {
        art.releaseAnimation()
    }

    private fun openDelay(index: Int): Float {
        return when (index) {
            SHAPE_INDEX_MAIN -> Motion.SHAPE_DELAY_MAIN_OPEN
            SHAPE_INDEX_ONE -> Motion.SHAPE_DELAY_ONE_OPEN
            else -> Motion.SHAPE_DELAY_TWO_OPEN
        }
    }

    private fun closeDelay(index: Int): Float {
        return when (index) {
            SHAPE_INDEX_MAIN -> Motion.SHAPE_DELAY_MAIN_CLOSE
            SHAPE_INDEX_ONE -> Motion.SHAPE_DELAY_ONE_CLOSE
            else -> Motion.SHAPE_DELAY_TWO_CLOSE
        }
    }

    private fun beginSwap() {
        swapFade.start(1f, 0f, Motion.SWAP_FADE_SECONDS, 0f, Easing.CSS_EASE)
        requestFrame()
    }

    private fun requestFrame() {
        frameRequester?.invoke()
    }

    private fun syncVisual() {
        visual.mirrorX = mirrorFlip.value
        visual.pressScaleX = pressX.value
        visual.pressScaleY = pressY.value
        settleTweens()
        for (index in shapeFades.indices) {
            shapeValues[index] = shapeFades[index].value
        }
        visual.shapeProgress = shapeValues
        visual.textAlpha = textFade.value * swapFade.value
        visual.lines = activeLines
        visual.gifActive = activeGif
    }

    /** 动画停下来的部分收拢到目标值，透明度不会停在中间状态。 */
    private fun settleTweens() {
        val shapes = if (bubbleShown) 1f else 0f
        for (tween in shapeFades) {
            if (!tween.isRunning) tween.jumpTo(shapes)
        }
        if (!textFade.isRunning) textFade.jumpTo(shapes)
        if (!swapFade.isRunning) swapFade.jumpTo(1f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.MeasureSpec.getSize(widthMeasureSpec),
            View.MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return
        when (layer) {
            Layer.WHALE -> art.drawWhaleLayer(canvas, unit, visual, viewportLeft, viewportTop)
            Layer.BUBBLE -> art.drawBubbleLayer(canvas, unit, visual, viewportLeft, viewportTop)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
            else -> return false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        art.releaseAnimation()
    }

    private fun handleDown(event: MotionEvent): Boolean {
        // 窗口在触摸过程中发生布局变化时系统会重新投递按下事件，手势进行中重复的按下直接忽略
        if (gestureActive) return true
        downRawX = event.rawX
        downRawY = event.rawY
        dragging = false
        target = resolveTarget(event.x, event.y)
        if (target == TouchTarget.NONE) return false
        gestureActive = true
        if (target == TouchTarget.WHALE) {
            pressDown()
            listener?.onPressChanged(true)
        }
        return true
    }

    private fun handleMove(event: MotionEvent) {
        if (target != TouchTarget.WHALE) return
        if (!dragging) {
            val offsetX = event.rawX - downRawX
            val offsetY = event.rawY - downRawY
            if (offsetX * offsetX + offsetY * offsetY >= Motion.DRAG_THRESHOLD_SQUARED) {
                dragging = true
                listener?.onStartDrag(downRawX, downRawY)
            }
        }
        if (dragging) listener?.onDrag(event.rawX, event.rawY)
    }

    private fun handleUp(event: MotionEvent) {
        val active = target
        gestureActive = false
        when (active) {
            TouchTarget.WHALE -> {
                pressUp()
                listener?.onPressChanged(false)
                if (dragging) {
                    listener?.onEndDrag()
                } else {
                    listener?.onWhaleTap()
                }
            }
            TouchTarget.BUBBLE -> {
                if (resolveTarget(event.x, event.y) == TouchTarget.BUBBLE) {
                    listener?.onBubbleTap()
                }
            }
            TouchTarget.NONE -> Unit
        }
        dragging = false
        target = TouchTarget.NONE
    }

    private fun handleCancel() {
        if (gestureActive && target == TouchTarget.WHALE) {
            pressUp()
            listener?.onPressChanged(false)
        }
        gestureActive = false
        dragging = false
        target = TouchTarget.NONE
    }

    private fun pressDown() {
        val host = pressHost
        if (host != null) {
            host.pressDown()
            return
        }
        if (reducedMotion) {
            pressX.jumpTo(PetGeometry.PRESS_SCALE_X)
            pressY.jumpTo(PetGeometry.PRESS_SCALE_Y)
            invalidate()
            return
        }
        pressX.start(
            pressX.value,
            PetGeometry.PRESS_SCALE_X,
            Motion.PRESS_SECONDS,
            0f,
            Easing.CSS_BACK_OUT
        )
        pressY.start(
            pressY.value,
            PetGeometry.PRESS_SCALE_Y,
            Motion.PRESS_SECONDS,
            0f,
            Easing.CSS_BACK_OUT
        )
        requestFrame()
    }

    private fun pressUp() {
        val host = pressHost
        if (host != null) {
            host.pressUp()
            return
        }
        if (reducedMotion) {
            pressX.jumpTo(1f)
            pressY.jumpTo(1f)
            invalidate()
            return
        }
        pressX.start(pressX.value, 1f, Motion.PRESS_SECONDS, 0f, Easing.CSS_BACK_OUT)
        pressY.start(pressY.value, 1f, Motion.PRESS_SECONDS, 0f, Easing.CSS_BACK_OUT)
        requestFrame()
    }

    /** 气泡层优先命中气泡形状，落在鲸鱼轮廓上的触摸按鲸鱼处理，其余不响应。 */
    private fun resolveTarget(x: Float, y: Float): TouchTarget {
        if (unit <= 0f) return TouchTarget.NONE
        if (layer == Layer.BUBBLE) {
            if (isInsideBubble(x, y)) return TouchTarget.BUBBLE
            return if (isInsideWhale(x, y)) TouchTarget.WHALE else TouchTarget.NONE
        }
        if (isInsideWhale(x, y)) return TouchTarget.WHALE
        return TouchTarget.NONE
    }

    /** 鲸鱼轮廓判定：把触点换算到贴图坐标后读取透明度，透明处不响应。 */
    private fun isInsideWhale(x: Float, y: Float): Boolean {
        val sizePx = PetGeometry.WHALE_SIZE * unit
        val whaleLeft = if (visualMirrored) 0f else PetGeometry.WHALE_LEFT
        val left = (whaleLeft - viewportLeft) * unit
        val top = (PetGeometry.WHALE_TOP - viewportTop) * unit
        if (x < left || x > left + sizePx || y < top || y > top + sizePx) return false
        val normalizedX = (x - left) / sizePx
        val normalizedY = (y - top) / sizePx
        val mapped = if (visualMirrored) 1f - normalizedX else normalizedX
        if (art.isWhaleOpaque(mapped, normalizedY)) return true
        return isMirroring && art.isWhaleOpaque(1f - mapped, normalizedY)
    }

    /** 气泡判定按实际绘制像素进行，翻转期间两种映射都接受。 */
    private fun isInsideBubble(x: Float, y: Float): Boolean {
        val canvasX = x / unit + viewportLeft
        val canvasY = y / unit + viewportTop
        val flippedX = if (visualMirrored) PetGeometry.CANVAS_WIDTH - canvasX else canvasX
        if (art.isBubbleOpaque(flippedX, canvasY)) return true
        return isMirroring && art.isBubbleOpaque(PetGeometry.CANVAS_WIDTH - flippedX, canvasY)
    }

    private enum class TouchTarget { NONE, WHALE, BUBBLE }

    companion object {
        private const val SWAP_SWITCH_POINT = 0.5f
        private const val SHAPE_INDEX_MAIN = 0
        private const val SHAPE_INDEX_ONE = 1

        /** 判定翻转过场是否结束的容差，用于区分动画中间值与两端。 */
        private const val FLIP_EPSILON = 0.001f
    }
}
