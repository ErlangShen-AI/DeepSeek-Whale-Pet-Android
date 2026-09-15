package com.whalepet.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import com.whalepet.R
import com.whalepet.core.Motion
import com.whalepet.core.PetGeometry
import com.whalepet.core.QuoteLine
import com.whalepet.core.QuoteStyle
import com.whalepet.ui.Typefaces
import kotlin.math.sqrt

/**
 * 桌宠的全部绘制。
 *
 * 绘制在 1026x1026 的根容器坐标中进行，根容器为正方形：鲸鱼贴右下角，气泡 SVG 占顶部一条。
 * 外部给出画布单位对应的像素数与当前窗口覆盖的区域，绘制时先平移到该区域左上角。
 */
class WhaleArt(context: Context) {

    /** 每帧绘制所需的状态。 */
    class Visual {
        var mirrorX = 1f
        var pressScaleX = 1f
        var pressScaleY = 1f
        var shapeProgress: FloatArray = FloatArray(SHAPE_COUNT)
        var textAlpha = 0f
        var gifActive = false
        var lines: List<QuoteLine?> = emptyList()

    }

    private val whale: Bitmap? = BitmapFactory.decodeResource(context.resources, R.drawable.whale)
    private val gif: Drawable? = AppCompatResources.getDrawable(context, R.drawable.rua)

    /** 气泡由主体与两个小圆组成，各自独立缩放与淡入。 */
    private val shapes: List<Drawable?> = listOf(
        AppCompatResources.getDrawable(context, R.drawable.bubble_main),
        AppCompatResources.getDrawable(context, R.drawable.bubble_dot_one),
        AppCompatResources.getDrawable(context, R.drawable.bubble_dot_two)
    )

    private val shapeCenters: List<Pair<Float, Float>> = listOf(
        PetGeometry.SHAPE_MAIN_CENTER_X to PetGeometry.SHAPE_MAIN_CENTER_Y,
        PetGeometry.SHAPE_ONE_CENTER_X to PetGeometry.SHAPE_ONE_CENTER_Y,
        PetGeometry.SHAPE_TWO_CENTER_X to PetGeometry.SHAPE_TWO_CENTER_Y
    )

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val typefaceCache = HashMap<Int, Typeface>()
    private val hitMask: IntArray = buildHitMask()

    private val whaleSource = Rect(0, 0, whale?.width ?: 1, whale?.height ?: 1)
    private val whaleTarget = RectF(
        PetGeometry.WHALE_LEFT,
        PetGeometry.WHALE_TOP,
        PetGeometry.CANVAS_WIDTH,
        PetGeometry.CANVAS_HEIGHT
    )
    private val bubbleBounds = Rect(
        0,
        0,
        PetGeometry.CANVAS_WIDTH.toInt(),
        PetGeometry.BUBBLE_HEIGHT.toInt()
    )
    private val gifBounds = RectF()

    /** 气泡三个图形的命中掩码，按各自绘制像素生成，尾巴与描边同样可点。 */
    private val bubbleMask: IntArray = buildBubbleMask()

    private fun buildBubbleMask(): IntArray {
        val mask = IntArray(BUBBLE_MASK_WIDTH * BUBBLE_MASK_HEIGHT)
        val bitmap = Bitmap.createBitmap(
            BUBBLE_MASK_WIDTH,
            BUBBLE_MASK_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(
            BUBBLE_MASK_WIDTH / PetGeometry.CANVAS_WIDTH,
            BUBBLE_MASK_HEIGHT / PetGeometry.BUBBLE_HEIGHT
        )
        for (drawable in shapes) {
            if (drawable == null) continue
            drawable.setBounds(bubbleBounds)
            drawable.alpha = 255
            drawable.draw(canvas)
        }
        bitmap.getPixels(mask, 0, BUBBLE_MASK_WIDTH, 0, 0, BUBBLE_MASK_WIDTH, BUBBLE_MASK_HEIGHT)
        bitmap.recycle()
        return mask
    }

    /** 画布坐标下的气泡轮廓判定，透明像素返回 false。 */
    fun isBubbleOpaque(canvasX: Float, canvasY: Float): Boolean {
        if (bubbleMask.isEmpty()) return true
        val x = (canvasX / PetGeometry.CANVAS_WIDTH * BUBBLE_MASK_WIDTH).toInt()
        val y = (canvasY / PetGeometry.BUBBLE_HEIGHT * BUBBLE_MASK_HEIGHT).toInt()
        if (x < 0 || y < 0 || x >= BUBBLE_MASK_WIDTH || y >= BUBBLE_MASK_HEIGHT) return false
        return ((bubbleMask[y * BUBBLE_MASK_WIDTH + x] ushr 24) and 0xFF) > HIT_ALPHA_THRESHOLD
    }

    /** 动图资源是否可用，缺失时台词组降级为文字。 */
    val hasGif: Boolean = gif != null

    /** 鲸鱼实际绘制范围，取贴图不透明像素的外接框，用于把窗口收紧到可见内容。 */
    val whaleBounds: RectF = buildHitBounds()

    private fun buildHitBounds(): RectF {
        if (hitMask.isEmpty()) return RectF(0f, 0f, 1f, 1f)
        var minX = HIT_MASK_SIZE
        var minY = HIT_MASK_SIZE
        var maxX = -1
        var maxY = -1
        for (y in 0 until HIT_MASK_SIZE) {
            for (x in 0 until HIT_MASK_SIZE) {
                val alpha = (hitMask[y * HIT_MASK_SIZE + x] ushr 24) and 0xFF
                if (alpha > HIT_ALPHA_THRESHOLD) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0 || maxY < 0) return RectF(0f, 0f, 1f, 1f)
        return RectF(
            minX.toFloat() / HIT_MASK_SIZE,
            minY.toFloat() / HIT_MASK_SIZE,
            (maxX + 1).toFloat() / HIT_MASK_SIZE,
            (maxY + 1).toFloat() / HIT_MASK_SIZE
        )
    }

    /** 归一化坐标下的轮廓判定，透明像素返回 false，用于触摸分流。 */
    fun isWhaleOpaque(normalizedX: Float, normalizedY: Float): Boolean {
        if (whale == null) return true
        val x = (normalizedX * HIT_MASK_SIZE).toInt().coerceIn(0, HIT_MASK_SIZE - 1)
        val y = (normalizedY * HIT_MASK_SIZE).toInt().coerceIn(0, HIT_MASK_SIZE - 1)
        return ((hitMask[y * HIT_MASK_SIZE + x] ushr 24) and 0xFF) > HIT_ALPHA_THRESHOLD
    }

    /** 预览用：一次画出鲸鱼与气泡。 */
    fun draw(canvas: Canvas, unit: Float, visual: Visual) {
        drawWhaleLayer(canvas, unit, visual, 0f, 0f)
        drawBubbleLayer(canvas, unit, visual, 0f, 0f)
    }

    /** 鲸鱼层，供鲸鱼窗口单独绘制。 */
    fun drawWhaleLayer(canvas: Canvas, unit: Float, visual: Visual, viewportLeft: Float, viewportTop: Float) {
        canvas.save()
        applyTransforms(canvas, unit, visual, viewportLeft, viewportTop)
        drawWhale(canvas)
        canvas.restore()
    }

    /** 气泡层，供气泡窗口单独绘制。 */
    fun drawBubbleLayer(canvas: Canvas, unit: Float, visual: Visual, viewportLeft: Float, viewportTop: Float) {
        canvas.save()
        applyTransforms(canvas, unit, visual, viewportLeft, viewportTop)
        drawBubble(canvas, visual)
        canvas.restore()
    }

    private fun applyTransforms(canvas: Canvas, unit: Float, visual: Visual, viewportLeft: Float, viewportTop: Float) {
        canvas.translate(-viewportLeft * unit, -viewportTop * unit)
        val canvasWidthPx = PetGeometry.CANVAS_WIDTH * unit
        if (visual.mirrorX != 1f) {
            canvas.translate(canvasWidthPx / 2f, 0f)
            canvas.scale(visual.mirrorX, 1f)
            canvas.translate(-canvasWidthPx / 2f, 0f)
        }
        if (visual.pressScaleX != 1f || visual.pressScaleY != 1f) {
            val pivotX = PetGeometry.PRESS_PIVOT_X * unit
            val pivotY = PetGeometry.PRESS_PIVOT_Y * unit
            canvas.translate(pivotX, pivotY)
            canvas.scale(visual.pressScaleX, visual.pressScaleY)
            canvas.translate(-pivotX, -pivotY)
        }
        canvas.scale(unit, unit)
    }

    /** 视图离开窗口时停掉动图播放，避免后台帧继续推进。 */
    fun releaseAnimation() {
        stopGif()
    }

    private fun drawWhale(canvas: Canvas) {
        val bitmap = whale ?: return
        canvas.drawBitmap(bitmap, whaleSource, whaleTarget, bitmapPaint)
    }

    private fun drawBubble(canvas: Canvas, visual: Visual) {
        if (visual.shapeProgress.all { it <= 0f }) {
            stopGif()
            return
        }
        for (index in shapes.indices) {
            val progress = visual.shapeProgress.getOrElse(index) { 0f }
            if (progress <= 0f) continue
            val drawable = shapes[index] ?: continue
            val center = shapeCenters[index]
            val scale = Motion.SHAPE_SCALE_FROM +
                (Motion.SHAPE_SCALE_TO - Motion.SHAPE_SCALE_FROM) * progress
            canvas.save()
            if (scale != 1f) {
                canvas.translate(center.first, center.second)
                canvas.scale(scale, scale)
                canvas.translate(-center.first, -center.second)
            }
            drawable.setBounds(bubbleBounds)
            drawable.alpha = (progress * 255f).toInt().coerceIn(0, 255)
            drawable.draw(canvas)
            canvas.restore()
        }
        if (visual.gifActive) {
            drawGif(canvas, visual)
            return
        }
        stopGif()
        drawText(canvas, visual)
    }

    private fun drawGif(canvas: Canvas, visual: Visual) {
        val drawable = gif ?: return
        startGif()
        drawable.alpha = (visual.textAlpha * 255f).toInt().coerceIn(0, 255)
        gifBounds.set(
            PetGeometry.ELLIPSE_CENTER_X - PetGeometry.GIF_FRAME_WIDTH / 2f,
            PetGeometry.ELLIPSE_CENTER_Y - PetGeometry.GIF_FRAME_HEIGHT / 2f,
            PetGeometry.ELLIPSE_CENTER_X + PetGeometry.GIF_FRAME_WIDTH / 2f,
            PetGeometry.ELLIPSE_CENTER_Y + PetGeometry.GIF_FRAME_HEIGHT / 2f
        )
        val intrinsicWidth = drawable.intrinsicWidth.toFloat()
        val intrinsicHeight = drawable.intrinsicHeight.toFloat()
        if (intrinsicWidth > 0f && intrinsicHeight > 0f) {
            val scale = minOf(
                gifBounds.width() / intrinsicWidth,
                gifBounds.height() / intrinsicHeight
            )
            val halfWidth = intrinsicWidth * scale / 2f
            val halfHeight = intrinsicHeight * scale / 2f
            drawable.setBounds(
                (gifBounds.centerX() - halfWidth).toInt(),
                (gifBounds.centerY() - halfHeight).toInt(),
                (gifBounds.centerX() + halfWidth).toInt(),
                (gifBounds.centerY() + halfHeight).toInt()
            )
        } else {
            drawable.setBounds(
                gifBounds.left.toInt(),
                gifBounds.top.toInt(),
                gifBounds.right.toInt(),
                gifBounds.bottom.toInt()
            )
        }
        drawable.draw(canvas)
    }

    private fun drawText(canvas: Canvas, visual: Visual) {
        val lines = visual.lines
        if (lines.isEmpty()) return
        val blockAlpha = visual.textAlpha
        if (blockAlpha <= 0f) return

        canvas.save()
        if (visual.mirrorX != 1f) {
            canvas.translate(PetGeometry.TEXT_CENTER_X, PetGeometry.TEXT_CENTER_Y)
            canvas.scale(-1f, 1f)
            canvas.translate(-PetGeometry.TEXT_CENTER_X, -PetGeometry.TEXT_CENTER_Y)
        }
        val heights = FloatArray(lines.size)
        var total = 0f
        for (index in lines.indices) {
            heights[index] = lineHeight(lines[index])
            total += heights[index]
        }
        var top = PetGeometry.TEXT_CENTER_Y - total / 2f
        for (index in lines.indices) {
            val line = lines[index]
            if (line != null) {
                drawLine(canvas, line, top, heights[index], (blockAlpha * 255f).toInt().coerceIn(0, 255))
            }
            top += heights[index]
        }
        canvas.restore()
    }

    private fun lineHeight(line: QuoteLine?): Float {
        if (line == null) return 0f
        return when (line.style) {
            QuoteStyle.LABEL -> PetGeometry.LABEL_SIZE * LABEL_LINE_HEIGHT
            QuoteStyle.AMOUNT -> PetGeometry.AMOUNT_SIZE * AMOUNT_LINE_HEIGHT
            QuoteStyle.PERIOD -> PetGeometry.PERIOD_SIZE * AMOUNT_LINE_HEIGHT
            QuoteStyle.HINT -> PetGeometry.HINT_MARGIN_TOP + maxOf(
                PetGeometry.HINT_SIZE * HINT_LINE_HEIGHT,
                PetGeometry.HINT_MIN_HEIGHT
            )
        }
    }

    private fun drawLine(canvas: Canvas, line: QuoteLine, top: Float, height: Float, alpha: Int) {
        prepareTextPaint(line, alpha)
        val limit = availableWidth(top + height / 2f)
        if (line.wrap) {
            val width = minOf(PetGeometry.WRAP_MAX_WIDTH, limit).coerceAtLeast(1f).toInt()
            val layout = StaticLayout.Builder
                .obtain(line.text, 0, line.text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.save()
            canvas.translate(
                PetGeometry.TEXT_CENTER_X - width / 2f,
                top + (height - layout.height) / 2f
            )
            layout.draw(canvas)
            canvas.restore()
            return
        }
        val baseSize = textPaint.textSize
        val measured = textPaint.measureText(line.text)
        if (limit > 0f && measured > limit) {
            val factor = (limit / measured).coerceAtLeast(PetGeometry.TEXT_SCALE_FLOOR)
            textPaint.textSize = baseSize * factor
        }
        val metrics = textPaint.fontMetrics
        val text = if (limit > 0f && textPaint.measureText(line.text) > limit) {
            TextUtils.ellipsize(line.text, textPaint, limit, TextUtils.TruncateAt.END).toString()
        } else {
            line.text
        }
        val textWidth = textPaint.measureText(text)
        val baseline = top + (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        canvas.drawText(text, PetGeometry.TEXT_CENTER_X - textWidth / 2f, baseline, textPaint)
    }

    /** 该行所在高度上椭圆内部的可用宽度，文字不会画到气泡描边之外。 */
    private fun availableWidth(centerY: Float): Float {
        val ratio = (centerY - PetGeometry.ELLIPSE_CENTER_Y) / PetGeometry.ELLIPSE_RADIUS_Y
        if (ratio <= -1f || ratio >= 1f) return 0f
        val chord = 2f * PetGeometry.ELLIPSE_RADIUS_X * sqrt(1f - ratio * ratio)
        return (chord - PetGeometry.TEXT_SIDE_PADDING).coerceAtLeast(0f)
    }

    private fun prepareTextPaint(line: QuoteLine, alpha: Int) {
        textPaint.textSize = when (line.style) {
            QuoteStyle.LABEL -> PetGeometry.LABEL_SIZE
            QuoteStyle.AMOUNT -> PetGeometry.AMOUNT_SIZE
            QuoteStyle.PERIOD -> PetGeometry.PERIOD_SIZE
            QuoteStyle.HINT -> PetGeometry.HINT_SIZE
        }
        textPaint.typeface = typeface(line.style)
        textPaint.color = line.color
            ?: if (line.style == QuoteStyle.HINT) PetGeometry.HINT_COLOR else PetGeometry.TEXT_COLOR
        textPaint.letterSpacing = when (line.style) {
            QuoteStyle.LABEL -> LABEL_TRACKING
            QuoteStyle.HINT -> HINT_TRACKING
            else -> 0f
        }
        textPaint.alpha = alpha
    }

    private fun typeface(style: QuoteStyle): Typeface {
        val weight = when (style) {
            QuoteStyle.LABEL -> PetGeometry.LABEL_WEIGHT
            QuoteStyle.AMOUNT, QuoteStyle.PERIOD -> PetGeometry.AMOUNT_WEIGHT
            QuoteStyle.HINT -> PetGeometry.HINT_WEIGHT
        }
        return typefaceCache.getOrPut(weight) { Typefaces.weighted(weight) }
    }

    private fun buildHitMask(): IntArray {
        val bitmap = whale ?: return IntArray(0)
        val scaled = Bitmap.createScaledBitmap(bitmap, HIT_MASK_SIZE, HIT_MASK_SIZE, true)
        val pixels = IntArray(HIT_MASK_SIZE * HIT_MASK_SIZE)
        scaled.getPixels(pixels, 0, HIT_MASK_SIZE, 0, 0, HIT_MASK_SIZE, HIT_MASK_SIZE)
        if (scaled !== bitmap) scaled.recycle()
        return pixels
    }

    private fun startGif() {
        val drawable = gif ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val animated = drawable as? AnimatedImageDrawable ?: return
        if (!animated.isRunning) animated.start()
    }

    private fun stopGif() {
        val drawable = gif ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val animated = drawable as? AnimatedImageDrawable ?: return
        if (animated.isRunning) animated.stop()
    }

    companion object {
        const val SHAPE_COUNT = 3

        private const val HIT_MASK_SIZE = 128
        private const val HIT_ALPHA_THRESHOLD = 8
        private const val BUBBLE_MASK_WIDTH = 128
        private const val BUBBLE_MASK_HEIGHT = 88
        private const val LABEL_LINE_HEIGHT = 1.15f
        private const val AMOUNT_LINE_HEIGHT = 1.05f
        private const val HINT_LINE_HEIGHT = 1.15f
        private const val LABEL_TRACKING = 0.06f
        private const val HINT_TRACKING = 0.02f
    }
}
