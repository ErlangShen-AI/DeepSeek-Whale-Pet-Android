package com.whalepet.pet

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.whalepet.R
import com.whalepet.core.BubbleContent
import com.whalepet.core.Diagnostics
import com.whalepet.core.Motion
import com.whalepet.core.MotionSettings
import com.whalepet.core.PetGeometry
import com.whalepet.core.QuoteLine
import com.whalepet.core.PetState
import com.whalepet.core.Prefs
import com.whalepet.core.SnapHorizontal
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮桌宠窗口。
 *
 * 鲸鱼与气泡各占一个窗口：鲸鱼窗口覆盖根容器下方那一条并常驻，位置与尺寸只在拖动和缩放时变化，
 * 因此气泡开合不会带动鲸鱼移动；气泡窗口只在气泡显示期间存在，收起后整条气泡区域从窗口范围移出，
 * 不拦截下层应用的触摸。两层共享同一份视觉状态，镜像与按压完全同步。
 */
class PetWindow(
    private val context: Context,
    private val prefs: Prefs,
    private val state: PetState,
    private val sound: PetSound
) : WhaleView.GestureListener {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val whaleView = WhaleView(context, WhaleView.Layer.WHALE)
    private val bubbleView = WhaleView(context, WhaleView.Layer.BUBBLE, whaleView.visual, whaleView.art)
    private val bubbleContent = BubbleContent(context)
    private val handler = Handler(Looper.getMainLooper())

    private val whaleParams = layoutParams()
    private val bubbleParams = layoutParams()
    private val layout = PetLayout()
    private val position = PetPosition()

    private val moveX = Tween()
    private val moveY = Tween()
    private val sizeTween = Tween()
    private var sizeFrom = 0f
    private var sizeTo = 0f

    private var boxWidth = 0f
    private var boxHeight = 0f
    private var boxX = 0f
    private var boxY = 0f
    private var unit = 0f

    private var screenWidth = 0
    private var screenHeight = 0
    private var insetLeft = 0f
    private var insetTop = 0f
    private var insetRight = 0f
    private var insetBottom = 0f

    private var attached = false
    private var bubbleWindowAdded = false
    private var animating = false
    private var lastFrameNanos = 0L
    private var dragging = false
    private var grabOffsetX = 0f
    private var grabOffsetY = 0f

    private var reducedMotion = false
    private var lastScale = 0f

    private val removeBubbleRunnable = Runnable { removeBubbleWindow() }
    private val prefsListener = Prefs.Listener { onPrefsChanged() }

    /** 气泡呈现状态机与它需要的窗口动作，窗口增删、内容推送与推帧都回到这里。 */
    private val presenter = BubblePresenter(
        prefs = prefs,
        state = state,
        content = bubbleContent,
        handler = handler,
        host = object : BubblePresenter.Host {
            override fun ensureBubbleWindow() {
                if (bubbleWindowAdded) return
                bubbleView.unit = unit
                addBubbleWindow()
            }

            override fun setBubbleTouchable(touchable: Boolean) {
                applyBubbleTouchable(touchable)
            }

            override fun scheduleBubbleRemoval() {
                handler.removeCallbacks(removeBubbleRunnable)
                handler.postDelayed(removeBubbleRunnable, BUBBLE_REMOVE_DELAY_MS)
            }

            override fun cancelBubbleRemoval() {
                handler.removeCallbacks(removeBubbleRunnable)
            }

            override fun bubbleVisible(): Boolean = whaleView.bubbleShown

            override fun gifAvailable(): Boolean = whaleView.hasGif

            override fun prefersReducedMotion(): Boolean = this@PetWindow.reducedMotion

            override fun openBubble() {
                whaleView.showBubble()
            }

            override fun closeBubble() {
                whaleView.hideBubble()
            }

            override fun pushContent(lines: List<QuoteLine?>, gif: Boolean, animated: Boolean) {
                whaleView.setContent(lines, gif, animated)
            }

            override fun requestFrames() {
                ensureAnimating()
            }
        }
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val previous = lastFrameNanos
            lastFrameNanos = frameTimeNanos
            val delta = if (previous == 0L) {
                0f
            } else {
                ((frameTimeNanos - previous) / 1_000_000_000.0).toFloat()
                    .coerceIn(0f, MAX_FRAME_SECONDS)
            }
            var active = false
            if (moveX.advance(delta)) active = true
            if (moveY.advance(delta)) active = true
            if (sizeTween.advance(delta)) {
                applyBoxSize(sizeFrom + (sizeTo - sizeFrom) * sizeTween.value, anchor = true)
                active = true
            }
            if (presenter.advanceRoll(delta)) active = true
            if (moveX.isRunning || moveY.isRunning) {
                boxX = moveX.value
                boxY = moveY.value
                applyLayout()
            }
            if (whaleView.advanceFrame(delta)) active = true
            if (bubbleWindowAdded) bubbleView.invalidate()
            if (active) {
                Choreographer.getInstance().postFrameCallback(this)
                return
            }
            animating = false
            applyLayout()
            persistPosition()
            whaleView.invalidate()
        }
    }

    init {
        whaleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.flags = bubbleParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        lastScale = prefs.scale
        whaleView.listener = this
        whaleView.frameRequester = { ensureAnimating() }
        bubbleView.listener = object : WhaleView.GestureListener by this {
            override fun onBubbleTap() {
                this@PetWindow.onBubbleTap()
            }
        }
        bubbleView.pressHost = whaleView
    }

    fun attach() {
        if (attached) return
        if (!Settings.canDrawOverlays(context)) return
        reducedMotion = MotionSettings.prefersReducedMotion(context)
        whaleView.applyReducedMotion(reducedMotion)
        bubbleView.applyReducedMotion(reducedMotion)
        updateMetrics()
        updateSize(anchorOnWhale = false)
        restorePosition()
        syncMoveTweens()
        whaleView.unit = unit
        whaleView.setMirrored(prefs.snapHorizontal == SnapHorizontal.LEFT, animated = false)
        fillWhaleParams()
        Diagnostics.attempt("添加鲸鱼窗口失败") { windowManager.addView(whaleView, whaleParams) }
        prefs.addListener(prefsListener)
        attached = true
        sound.apply(prefs)
        applyLayout()
    }

    fun detach() {
        if (!attached) return
        prefs.removeListener(prefsListener)
        presenter.cancelPending()
        handler.removeCallbacks(removeBubbleRunnable)
        removeBubbleWindow()
        whaleView.stopAnimations()
        windowManager.removeView(whaleView)
        attached = false
    }

    fun onSnapshot(snapshot: PetState.Snapshot) {
        presenter.onSnapshot(snapshot)
    }

    /** 屏幕尺寸或旋转变化时按锚点结算位置，并补齐窗口与重绘。 */
    fun refreshMetrics() {
        if (!attached) return
        updateMetrics()
        sizeTween.jumpTo(1f)
        updateSize(anchorOnWhale = true)
        clampPosition()
        ensureWindowsPresent()
        applyLayout()
        whaleView.invalidate()
        if (bubbleWindowAdded) bubbleView.invalidate()
    }

    /** 按压与松手取自同一个状态入口，按压形变与音效在同一帧开始。 */
    override fun onPressChanged(pressed: Boolean) {
        sound.setPressed(pressed)
    }

    override fun onStartDrag(rawX: Float, rawY: Float) {
        dragging = true
        grabOffsetX = rawX - boxX
        grabOffsetY = rawY - boxY
        moveX.jumpTo(boxX)
        moveY.jumpTo(boxY)
    }

    override fun onDrag(rawX: Float, rawY: Float) {
        if (!dragging) return
        boxX = (rawX - grabOffsetX).coerceIn(minBoxX(), maxBoxX())
        boxY = (rawY - grabOffsetY).coerceIn(minBoxY(), maxBoxY())
        applyLayout()
    }

    override fun onEndDrag() {
        if (!dragging) return
        dragging = false
        settle()
    }

    override fun onWhaleTap() {
        presenter.onWhaleTap()
    }

    override fun onBubbleTap() {
        presenter.onBubbleTap()
    }

    /** 气泡淡出与待移除期间不接收触摸，鲸鱼上的触摸不会被矩形窗口占用。 */
    private fun applyBubbleTouchable(touchable: Boolean) {
        if (!bubbleWindowAdded) return
        bubbleParams.flags = if (touchable) {
            bubbleParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            bubbleParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        Diagnostics.attempt("更新气泡窗口失败") { windowManager.updateViewLayout(bubbleView, bubbleParams) }
    }

    private fun settle() {
        val targetX = snapTargetX(boxX)
        val targetY = snapTargetY(boxY)
        if (reducedMotion || abs(targetX - boxX) < POSITION_TOLERANCE &&
            abs(targetY - boxY) < POSITION_TOLERANCE
        ) {
            moveX.jumpTo(targetX)
            moveY.jumpTo(targetY)
            boxX = targetX
            boxY = targetY
            applyLayout()
            persistPosition()
            return
        }
        moveX.start(boxX, targetX, Motion.SNAP_SECONDS, 0f, Easing.CSS_EASE)
        moveY.start(boxY, targetY, Motion.SNAP_SECONDS, 0f, Easing.CSS_EASE)
        ensureAnimating()
    }

    private fun snapTargetX(projectedX: Float): Float {
        val snapped = position.snapX(projectedX)
        prefs.snapHorizontal = snapped.edge
        setMirrored(snapped.edge == SnapHorizontal.LEFT)
        return snapped.value
    }

    private fun snapTargetY(projectedY: Float): Float {
        val snapped = position.snapY(projectedY)
        prefs.snapVertical = snapped.edge
        return snapped.value
    }

    private fun setMirrored(mirrored: Boolean) {
        whaleView.setMirrored(mirrored, animated = true)
        if (bubbleWindowAdded) bubbleView.invalidate()
    }

    private fun ensureAnimating() {
        if (animating || !attached) return
        animating = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun updateMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = Diagnostics.attempt("读取窗口度量失败") { windowManager.currentWindowMetrics }
            if (metrics != null) {
                val bounds = metrics.bounds
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    readLegacyMetrics()
                    return
                }
                screenWidth = bounds.width()
                screenHeight = bounds.height()
                val insets = metrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                insetLeft = insets.left.toFloat()
                insetTop = insets.top.toFloat()
                insetRight = insets.right.toFloat()
                insetBottom = insets.bottom.toFloat()
                syncPosition()
                return
            }
        }
        readLegacyMetrics()
    }

    /** 低版本没有窗口度量接口，用真实尺寸与可用尺寸之差近似系统栏。 */
    @Suppress("DEPRECATION")
    private fun readLegacyMetrics() {
        val display = windowManager.defaultDisplay
        val real = Point()
        display.getRealSize(real)
        val usable = Point()
        display.getSize(usable)
        screenWidth = real.x
        screenHeight = real.y
        insetLeft = 0f
        insetTop = 0f
        insetRight = (real.x - usable.x).coerceAtLeast(0).toFloat()
        insetBottom = (real.y - usable.y).coerceAtLeast(0).toFloat()
        syncPosition()
    }

    /** 把当前屏幕几何交给位置计算。 */
    private fun syncPosition() {
        position.configure(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            insetLeft = insetLeft,
            insetTop = insetTop,
            insetRight = insetRight,
            insetBottom = insetBottom
        )
    }

    private fun updateSize(anchorOnWhale: Boolean) {
        val target = computeBoxWidth()
        if (!anchorOnWhale || boxWidth <= 0f || reducedMotion ||
            abs(target - boxWidth) < POSITION_TOLERANCE
        ) {
            sizeTween.jumpTo(1f)
            applyBoxSize(target, anchorOnWhale)
            return
        }
        sizeFrom = boxWidth
        sizeTo = target
        sizeTween.start(0f, 1f, Motion.SIZE_SECONDS, 0f, Easing.CSS_EASE)
        ensureAnimating()
    }

    /** 按给定边长重排窗口，缩放过程固定鲸鱼所在的那个角。 */
    private fun applyBoxSize(width: Float, anchor: Boolean) {
        if (width <= 0f) return
        val height = width * PetGeometry.CANVAS_HEIGHT / PetGeometry.CANVAS_WIDTH
        if (anchor && boxWidth > 0f) {
            val anchorX = if (whaleView.isMirrored) boxX else boxX + boxWidth
            val anchorY = boxY + boxHeight
            boxX = if (whaleView.isMirrored) anchorX else anchorX - width
            boxY = anchorY - height
        }
        syncMoveTweens()
        boxWidth = width
        boxHeight = height
        position.resize(boxWidth, boxHeight)
        unit = boxWidth / PetGeometry.CANVAS_WIDTH
        whaleView.unit = unit
        bubbleView.unit = unit
        clampPosition()
        applyLayout()
    }

    private fun computeBoxWidth(): Float {
        return layout.boxWidth(
            shortestPx = minOf(screenWidth, screenHeight).toFloat(),
            density = context.resources.displayMetrics.density,
            scale = prefs.scale
        )
    }

    private fun restorePosition() {
        boxX = position.xOfFraction(prefs.positionFractionX)
        boxY = position.yOfFraction(prefs.positionFractionY)
    }

    private fun clampPosition() {
        boxX = position.clampX(boxX)
        boxY = position.clampY(boxY)
    }

    private fun persistPosition() {
        if (screenWidth <= 0 || screenHeight <= 0) return
        prefs.positionFractionX = position.fractionOfX(boxX)
        prefs.positionFractionY = position.fractionOfY(boxY)
    }

    /** 让位置插值跟随当前坐标，避免尺寸动画时读到过期数值。 */
    private fun syncMoveTweens() {
        moveX.jumpTo(boxX)
        moveY.jumpTo(boxY)
    }

    /** 气泡窗口在画布中的矩形，取按压形变后的范围；镜像状态与鲸鱼层保持一致，翻转期间取两侧的并集。 */
    private fun pressedBubbleRect(): RectF =
        layout.bubbleRect(whaleView.isMirrored, whaleView.isMirroring)

    private fun applyLayout() {
        if (!attached) return
        fillWhaleParams()
        if (whaleView.isAttachedToWindow) {
            Diagnostics.attempt("更新鲸鱼窗口失败") {
                windowManager.updateViewLayout(whaleView, whaleParams)
            }
        }
        if (bubbleWindowAdded) {
            applyBubbleParams()
            if (bubbleView.isAttachedToWindow) {
                Diagnostics.attempt("更新气泡窗口失败") {
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                }
            }
        }
    }

    /** 按当前几何填写鲸鱼窗口的位置、尺寸与绘制视口。 */
    private fun fillWhaleParams() {
        val margin = PetGeometry.WHALE_WINDOW_SLACK
        val rect = layout.whaleRect(
            bounds = whaleView.whaleBounds,
            mirrored = whaleView.isMirrored,
            mirroring = whaleView.isMirroring,
            margin = margin
        )
        val widthPx = (rect.width() * unit).roundToInt()
        val heightPx = (rect.height() * unit).roundToInt()
        if (widthPx <= 0 || heightPx <= 0) return
        whaleView.setViewport(rect.left, rect.top)
        whaleParams.width = widthPx
        whaleParams.height = heightPx
        whaleParams.x = (boxX + rect.left * unit).roundToInt()
        whaleParams.y = (boxY + rect.top * unit).roundToInt()
    }

    /** 窗口在系统中丢失时重新挂上，气泡窗口丢失时同时收起气泡状态。 */
    private fun ensureWindowsPresent() {
        if (!attached) return
        if (!whaleView.isAttachedToWindow) {
            fillWhaleParams()
            Diagnostics.attempt("重新挂载鲸鱼窗口失败") { windowManager.addView(whaleView, whaleParams) }
        }
        if (bubbleWindowAdded && !bubbleView.isAttachedToWindow) {
            bubbleWindowAdded = false
            whaleView.hideBubble()
        }
    }

    private fun addBubbleWindow() {
        if (!attached || bubbleWindowAdded) return
        applyBubbleParams()
        bubbleParams.flags = bubbleParams.flags and
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        Diagnostics.attempt("添加气泡窗口失败") { windowManager.addView(bubbleView, bubbleParams) }
        bubbleWindowAdded = true
        bubbleView.invalidate()
    }

    /** 气泡窗口的位置、尺寸与绘制视口，都取按压形变后的范围，加入窗口与排版共用这一处计算。 */
    private fun applyBubbleParams() {
        val rect = pressedBubbleRect()
        bubbleView.setViewport(rect.left, rect.top)
        bubbleParams.width = (rect.width() * unit).roundToInt()
        bubbleParams.height = (rect.height() * unit).roundToInt()
        bubbleParams.x = (boxX + rect.left * unit).roundToInt()
        bubbleParams.y = (boxY + rect.top * unit).roundToInt()
    }

    private fun removeBubbleWindow() {
        if (!bubbleWindowAdded) return
        Diagnostics.attempt("移除气泡窗口失败") { windowManager.removeView(bubbleView) }
        bubbleWindowAdded = false
    }

    private fun minBoxX(): Float = position.minX

    private fun maxBoxX(): Float = position.maxX

    private fun minBoxY(): Float = position.minY

    private fun maxBoxY(): Float = position.maxY

    private fun onPrefsChanged() {
        if (!attached) return
        val contentChanged = presenter.refreshSettings()
        if (abs(prefs.scale - lastScale) > SCALE_TOLERANCE) {
            lastScale = prefs.scale
            updateSize(anchorOnWhale = true)
        }
        if (prefs.bubbleSeconds <= 0) presenter.cancelAutoHide()
        sound.apply(prefs)
        // 位置与缩放写入同样会触发通知，只有影响内容的开关变化才刷新
        if (contentChanged) presenter.refreshVisibleContent()
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            0,
            0,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
    }

    companion object {
        private const val MAX_FRAME_SECONDS = 0.05f
        private const val SCALE_TOLERANCE = 0.001f
        private const val POSITION_TOLERANCE = 0.5f
        private const val BUBBLE_REMOVE_DELAY_MS = 550L
    }
}
