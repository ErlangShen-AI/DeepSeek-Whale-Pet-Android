package com.whalepet.pet

import com.whalepet.core.SnapHorizontal
import com.whalepet.core.SnapVertical

/**
 * 桌宠在屏幕内允许出现的范围与吸附结算。
 *
 * 接收当前屏幕尺寸、系统栏内边距与画布边长后，给出坐标的上下限、屏幕比例换算与吸附目标，
 * 自身不持有窗口与配置。
 */
class PetPosition {

    private var screenWidth = 0
    private var screenHeight = 0
    private var insetLeft = 0f
    private var insetTop = 0f
    private var insetRight = 0f
    private var insetBottom = 0f
    private var boxWidth = 0f
    private var boxHeight = 0f

    /** 接收屏幕尺寸与系统栏内边距。 */
    fun configure(
        screenWidth: Int,
        screenHeight: Int,
        insetLeft: Float,
        insetTop: Float,
        insetRight: Float,
        insetBottom: Float
    ) {
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        this.insetLeft = insetLeft
        this.insetTop = insetTop
        this.insetRight = insetRight
        this.insetBottom = insetBottom
    }

    /** 接收当前画布边长。 */
    fun resize(boxWidth: Float, boxHeight: Float) {
        this.boxWidth = boxWidth
        this.boxHeight = boxHeight
    }

    val minX: Float
        get() = insetLeft

    val maxX: Float
        get() = (screenWidth - boxWidth - insetRight).coerceAtLeast(insetLeft)

    val minY: Float
        get() = insetTop

    val maxY: Float
        get() = (screenHeight - boxHeight - insetBottom).coerceAtLeast(insetTop)

    fun clampX(value: Float): Float = value.coerceIn(minX, maxX)

    fun clampY(value: Float): Float = value.coerceIn(minY, maxY)

    fun xOfFraction(fraction: Float): Float = minX + fraction * (maxX - minX).coerceAtLeast(0f)

    fun yOfFraction(fraction: Float): Float = minY + fraction * (maxY - minY).coerceAtLeast(0f)

    /** 坐标转屏幕比例，跨度至少按 1 像素计算，避免除零。 */
    fun fractionOfX(value: Float): Float = (value - minX) / (maxX - minX).coerceAtLeast(1f)

    fun fractionOfY(value: Float): Float = (value - minY) / (maxY - minY).coerceAtLeast(1f)

    /** 横向吸附：中心落在屏幕左侧四分之一贴左边，落在右侧四分之一贴右边，其余夹在范围内。 */
    fun snapX(value: Float): SnapResult {
        val center = value + boxWidth / 2f
        val quarter = screenWidth / 4f
        return when {
            center < quarter -> SnapResult(minX, SnapHorizontal.LEFT)
            center > quarter * 3f -> SnapResult(maxX, SnapHorizontal.RIGHT)
            else -> SnapResult(clampX(value), SnapHorizontal.NONE)
        }
    }

    /** 纵向吸附，判定方式与横向一致。 */
    fun snapY(value: Float): VerticalSnapResult {
        val center = value + boxHeight / 2f
        val quarter = screenHeight / 4f
        return when {
            center < quarter -> VerticalSnapResult(minY, SnapVertical.TOP)
            center > quarter * 3f -> VerticalSnapResult(maxY, SnapVertical.BOTTOM)
            else -> VerticalSnapResult(clampY(value), SnapVertical.NONE)
        }
    }

    class SnapResult(val value: Float, val edge: SnapHorizontal)

    class VerticalSnapResult(val value: Float, val edge: SnapVertical)
}
