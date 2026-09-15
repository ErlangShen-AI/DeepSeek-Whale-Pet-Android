package com.whalepet.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.whalepet.core.PetGeometry
import com.whalepet.core.QuoteLine
import com.whalepet.pet.WhaleArt
import kotlin.math.roundToInt

/** 设置页顶部的桌宠预览，按控件尺寸等比铺满画布并始终展示气泡内容。 */
class PetPreviewView(context: Context) : View(context) {

    private val art = WhaleArt(context)
    private val visual = WhaleArt.Visual()

    var lines: List<QuoteLine?> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * PetGeometry.CANVAS_HEIGHT / PetGeometry.CANVAS_WIDTH).roundToInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = minOf(
            width / PetGeometry.CANVAS_WIDTH,
            height / PetGeometry.CANVAS_HEIGHT
        ) * PREVIEW_SCALE
        if (unit <= 0f) return
        visual.shapeProgress = FloatArray(WhaleArt.SHAPE_COUNT) { 1f }
        visual.textAlpha = 1f
        visual.gifActive = false
        visual.lines = lines
        canvas.save()
        canvas.translate(
            (width - PetGeometry.CANVAS_WIDTH * unit) / 2f,
            (height - PetGeometry.CANVAS_HEIGHT * unit) / 2f
        )
        art.draw(canvas, unit, visual)
        canvas.restore()
    }

    companion object {
        /** 预览相对卡片宽度的比例，四周留出余量。 */
        private const val PREVIEW_SCALE = 0.72f
    }
}
