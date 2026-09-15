package com.whalepet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.whalepet.R
import com.whalepet.core.Motion
import com.whalepet.pet.Easing
import com.whalepet.pet.Spring
import kotlin.math.roundToInt

/**
 * 分段选择控件。
 *
 * 选中项由一个可移动的底板指示，点击后底板以弹簧滑向目标分段，
 * 标签颜色随底板位置连续过渡，按下即给出反馈，抬起才提交选择。
 */
class SegmentedControl(
    context: Context,
    private val metrics: Metrics,
    private val options: List<String>,
    initialIndex: Int,
    private val onSelected: (Int) -> Unit
) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val pillRect = RectF()

    private val idleColor = ContextCompat.getColor(context, R.color.text_secondary)
    private val activeColor = ContextCompat.getColor(context, R.color.accent)

    private val pillX = Spring(0f)
    private val pillWidth = Spring(0f)
    private var selection = initialIndex.coerceIn(0, options.size - 1)
    private var pressedIndex = -1
    private var animating = false
    private var lastFrameNanos = 0L

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
            if (pillX.advance(delta)) active = true
            if (pillWidth.advance(delta)) active = true
            invalidate()
            if (active) {
                Choreographer.getInstance().postFrameCallback(this)
                return
            }
            animating = false
        }
    }

    init {
        isClickable = true
        setWillNotDraw(false)
        textPaint.typeface = Typefaces.weighted(SEGMENT_WEIGHT)
        textPaint.textAlign = Paint.Align.CENTER
        trackPaint.color = ContextCompat.getColor(context, R.color.segment_track)
        pillPaint.color = ContextCompat.getColor(context, R.color.surface_card)
    }

    fun setSelected(index: Int, animated: Boolean) {
        val target = index.coerceIn(0, options.size - 1)
        if (target == selection && pillWidth.value > 0f) return
        selection = target
        animatePill(animated)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        textPaint.textSize = metrics.text(TextRole.SUBHEAD)
        snapPill()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) {
            View.MeasureSpec.getSize(heightMeasureSpec)
        } else {
            (metrics.lineHeight(TextRole.SUBHEAD) + 2f * metrics.uf(SEGMENT_PADDING_V)).toInt()
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        trackRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val inset = metrics.uf(SEGMENT_INSET)
        pillRect.set(
            pillX.value,
            inset,
            pillX.value + pillWidth.value,
            height - inset
        )
        val pillRadius = (pillRect.height()) / 2f
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)

        val segmentWidth = width.toFloat() / options.size
        for (index in options.indices) {
            val centerX = segmentWidth * (index + 0.5f)
            val ratio = pillOverlapRatio(index, segmentWidth)
            textPaint.color = blend(idleColor, activeColor, ratio)
            val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(options[index], centerX, baseline, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = indexAt(event.x)
                if (pressedIndex >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    pressVisual(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedIndex >= 0 && indexAt(event.x) != pressedIndex) pressVisual(false)
            }
            MotionEvent.ACTION_UP -> {
                val index = indexAt(event.x)
                pressVisual(false)
                if (index >= 0 && index == pressedIndex) {
                    if (index != selection) {
                        selection = index
                        animatePill(true)
                        onSelected(index)
                    }
                    performClick()
                }
                pressedIndex = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                pressVisual(false)
                pressedIndex = -1
            }
            else -> return false
        }
        return true
    }

    private fun pressVisual(pressed: Boolean) {
        animate().cancel()
        animate()
            .scaleX(if (pressed) PRESS_SCALE else 1f)
            .scaleY(if (pressed) PRESS_SCALE else 1f)
            .setDuration(PRESS_DURATION_MILLIS)
            .setInterpolator(null)
            .start()
    }

    private fun indexAt(x: Float): Int {
        if (x < 0f || x > width) return -1
        val segmentWidth = width.toFloat() / options.size
        return (x / segmentWidth).toInt().coerceIn(0, options.size - 1)
    }

    private fun pillOverlapRatio(index: Int, segmentWidth: Float): Float {
        if (pillWidth.value <= 0f) return if (index == selection) 1f else 0f
        val segmentStart = segmentWidth * index
        val overlap = minOf(pillX.value + pillWidth.value, segmentStart + segmentWidth) -
            maxOf(pillX.value, segmentStart)
        return (overlap / segmentWidth).coerceIn(0f, 1f)
    }

    private fun snapPill() {
        val segmentWidth = width.toFloat() / options.size
        val inset = metrics.uf(SEGMENT_INSET)
        val pillW = segmentWidth - inset * 2f
        pillX.snapTo(segmentWidth * selection + inset)
        pillWidth.snapTo(pillW)
        invalidate()
    }

    private fun animatePill(animated: Boolean) {
        val segmentWidth = width.toFloat() / options.size
        val inset = metrics.uf(SEGMENT_INSET)
        val targetX = segmentWidth * selection + inset
        val targetWidth = segmentWidth - inset * 2f
        if (!animated) {
            pillX.snapTo(targetX)
            pillWidth.snapTo(targetWidth)
            invalidate()
            return
        }
        pillX.animateTo(targetX, Motion.FREE_RESPONSE, Motion.FREE_DAMPING, 0f)
        pillWidth.animateTo(targetWidth, Motion.FREE_RESPONSE, Motion.FREE_DAMPING, 0f)
        startAnimating()
    }

    private fun startAnimating() {
        if (animating) return
        animating = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        val curve = Easing.EASE_OUT(ratio)
        val red = (android.graphics.Color.red(from) +
            (android.graphics.Color.red(to) - android.graphics.Color.red(from)) * curve).roundToInt()
        val green = (android.graphics.Color.green(from) +
            (android.graphics.Color.green(to) - android.graphics.Color.green(from)) * curve).roundToInt()
        val blue = (android.graphics.Color.blue(from) +
            (android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * curve).roundToInt()
        val alpha = (android.graphics.Color.alpha(from) +
            (android.graphics.Color.alpha(to) - android.graphics.Color.alpha(from)) * curve).roundToInt()
        return android.graphics.Color.argb(alpha, red, green, blue)
    }

    companion object {
        /** 标签上下留出的内边距，单位为 unit 的倍数。 */
        private const val SEGMENT_PADDING_V = 1.6f
        private const val SEGMENT_INSET = 0.3f
        private const val SEGMENT_WEIGHT = 600
        private const val PRESS_SCALE = 0.97f
        private const val PRESS_DURATION_MILLIS = 120L
        private const val MAX_FRAME_SECONDS = 0.05f
    }
}
