package com.whalepet.pet

import android.graphics.RectF
import com.whalepet.core.PetGeometry

/**
 * 悬浮窗的几何计算。
 *
 * 画布边长、两个窗口各自的画布矩形与镜像翻转期间的并集都在这里算，
 * 只依赖屏幕尺寸、画布内边距与内容外接框，不持有窗口、视图与配置。
 */
class PetLayout {

    /** 画布边长：屏幕短边的比例，乘缩放倍率前先按上限封顶，最后夹在上下限之间。 */
    fun boxWidth(shortestPx: Float, density: Float, scale: Float): Float {
        val baseDp = (shortestPx / density * PetGeometry.BASE_FRACTION)
            .coerceAtMost(PetGeometry.BASE_CAP_DP) * scale
        val widthPx = baseDp.coerceIn(PetGeometry.MIN_BASE_DP, PetGeometry.MAX_BASE_DP) * density
        return widthPx.coerceAtMost(shortestPx)
    }

    /**
     * 鲸鱼窗口在画布中的矩形。
     *
     * 以贴图不透明像素的外接框为基准，叠加按压形变后的范围与四周余量；
     * 镜像翻转期间取两侧并集，因此翻转过程中不会被窗口裁掉。
     */
    fun whaleRect(bounds: RectF, mirrored: Boolean, mirroring: Boolean, margin: Float): RectF {
        val plain = PetGeometry.pressedBounds(
            RectF(
                PetGeometry.WHALE_LEFT + bounds.left * PetGeometry.WHALE_SIZE,
                PetGeometry.WHALE_TOP + bounds.top * PetGeometry.WHALE_SIZE,
                PetGeometry.WHALE_LEFT + bounds.right * PetGeometry.WHALE_SIZE,
                PetGeometry.WHALE_TOP + bounds.bottom * PetGeometry.WHALE_SIZE
            )
        )
        val flipped = mirror(plain)
        val rect = when {
            mirroring -> union(plain, flipped)
            mirrored -> flipped
            else -> plain
        }
        return RectF(rect.left - margin, rect.top - margin, rect.right + margin, rect.bottom + margin)
    }

    /**
     * 气泡窗口在画布中的矩形。
     *
     * 以气泡三个图形实际绘制的像素外接框为基准，叠加按压形变后的范围；
     * 镜像翻转期间同样取两侧并集。
     */
    fun bubbleRect(mirrored: Boolean, mirroring: Boolean): RectF {
        val plain = PetGeometry.pressedBounds(
            RectF(
                PetGeometry.BUBBLE_WINDOW_LEFT,
                PetGeometry.BUBBLE_WINDOW_TOP,
                PetGeometry.BUBBLE_WINDOW_RIGHT,
                PetGeometry.BUBBLE_WINDOW_BOTTOM
            )
        )
        val flipped = mirror(plain)
        return when {
            mirroring -> union(plain, flipped)
            mirrored -> flipped
            else -> plain
        }
    }

    private fun mirror(rect: RectF): RectF = RectF(
        PetGeometry.CANVAS_WIDTH - rect.right,
        rect.top,
        PetGeometry.CANVAS_WIDTH - rect.left,
        rect.bottom
    )

    private fun union(first: RectF, second: RectF): RectF = RectF(
        minOf(first.left, second.left),
        minOf(first.top, second.top),
        maxOf(first.right, second.right),
        maxOf(first.bottom, second.bottom)
    )
}
