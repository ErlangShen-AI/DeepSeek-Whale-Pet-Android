package com.whalepet.core

import android.graphics.RectF

/**
 * 桌宠绘制所需的几何与配色常量。
 *
 * 全部数值以 1026x700 的画布单位为基准，绘制时统一乘以「画布单位转像素」的比例，
 * 因此任何屏幕尺寸与缩放下都不需要单独调整。
 */
object PetGeometry {

    /** 根容器为正方形，宽高同为 1026 个单位。 */
    const val CANVAS_WIDTH = 1026f
    const val CANVAS_HEIGHT = 1026f

    /** 气泡 SVG 的视口高度，位于根容器顶部。 */
    const val BUBBLE_HEIGHT = 700f

    /** 鲸鱼贴图贴根容器右下角时的边长。 */
    const val WHALE_SIZE = 610f

    /** 鲸鱼贴图的左上角，等于根容器减去鲸鱼边长。 */
    const val WHALE_LEFT = CANVAS_WIDTH - WHALE_SIZE
    const val WHALE_TOP = CANVAS_HEIGHT - WHALE_SIZE

    /** 鲸鱼窗口在形变范围之外留出的余量，覆盖形变缓动的过冲与像素取整，单位为画布单位。 */
    const val WHALE_WINDOW_SLACK = 16f

    /** 画布宽度取屏幕短边的比例。 */
    const val BASE_FRACTION = 0.28f

    /** 比例结果在乘缩放倍率前的上限，单位为 dp。 */
    const val BASE_CAP_DP = 250f

    /** 画布宽度的上下限，单位为 dp。 */
    const val MIN_BASE_DP = 122f
    const val MAX_BASE_DP = 625f

    /** 默认缩放倍率，用于保证触摸目标与文字可读性。 */
    const val DEFAULT_SCALE = 1.5f

    const val MIN_SCALE = 0.6f
    const val MAX_SCALE = 2.5f

    /** 文字块中心在画布中的位置，对应气泡大椭圆的视觉中心。 */
    const val TEXT_CENTER_X = 0.4425f * CANVAS_WIDTH
    const val TEXT_CENTER_Y = 0.38f * BUBBLE_HEIGHT

    /** 按压形变的原点，取根容器底边中心。 */
    const val PRESS_PIVOT_X = CANVAS_WIDTH / 2f
    const val PRESS_PIVOT_Y = CANVAS_HEIGHT

    /** 按压时横向放大、纵向压扁的比例，窗口要覆盖形变后的范围。 */
    const val PRESS_SCALE_X = 1.05f
    const val PRESS_SCALE_Y = 0.88f

    /** 某个画布矩形在按压形变前后的外接框。 */
    fun pressedBounds(rect: RectF): RectF {
        val pressedLeft = PRESS_PIVOT_X + (rect.left - PRESS_PIVOT_X) * PRESS_SCALE_X
        val pressedRight = PRESS_PIVOT_X + (rect.right - PRESS_PIVOT_X) * PRESS_SCALE_X
        val pressedTop = PRESS_PIVOT_Y + (rect.top - PRESS_PIVOT_Y) * PRESS_SCALE_Y
        val pressedBottom = PRESS_PIVOT_Y + (rect.bottom - PRESS_PIVOT_Y) * PRESS_SCALE_Y
        return RectF(
            minOf(rect.left, pressedLeft),
            minOf(rect.top, pressedTop),
            maxOf(rect.right, pressedRight),
            maxOf(rect.bottom, pressedBottom)
        )
    }

    /** 气泡三个图形各自的中心，缩放动画以各自中心为原点。 */
    const val SHAPE_MAIN_CENTER_X = 454f
    const val SHAPE_MAIN_CENTER_Y = 250f
    const val SHAPE_ONE_CENTER_X = 352f
    const val SHAPE_ONE_CENTER_Y = 561f
    const val SHAPE_TWO_CENTER_X = 442f
    const val SHAPE_TWO_CENTER_Y = 646f

    /** 气泡大椭圆的中心与半径，动图显示区域由它内缩得到。 */
    const val ELLIPSE_CENTER_X = 454f
    const val ELLIPSE_CENTER_Y = 247f
    const val ELLIPSE_RADIUS_X = 373f
    const val ELLIPSE_RADIUS_Y = 232f
    const val GIF_INSET_RATIO = 0.86f
    const val GIF_FRAME_WIDTH = 2f * ELLIPSE_RADIUS_X * GIF_INSET_RATIO
    const val GIF_FRAME_HEIGHT = 2f * ELLIPSE_RADIUS_Y * GIF_INSET_RATIO

    /**
     * 气泡窗口覆盖的范围。
     *
     * 取主体椭圆、尾巴与两个小圆在按压形变后的外接矩形，窗口只覆盖这一块，
     * 鲸鱼其余可抓取的区域不会被气泡窗口挡住。
     */
    const val BUBBLE_WINDOW_LEFT = 72f
    const val BUBBLE_WINDOW_RIGHT = 836f
    const val BUBBLE_WINDOW_TOP = 6f
    const val BUBBLE_WINDOW_BOTTOM = 673f

    /** 三行文字的字号，单位为画布单位。 */
    const val LABEL_SIZE = 66f
    const val AMOUNT_SIZE = 128f
    const val PERIOD_SIZE = 104f
    const val HINT_SIZE = 56f

    /** 提示行的上边距与最小高度，保证内容切换时气泡不跳动。 */
    const val HINT_MARGIN_TOP = 9f
    const val HINT_MIN_HEIGHT = 64f

    /** 台词换行时的最大行宽。 */
    const val WRAP_MAX_WIDTH = 560f

    /** 文字左右两侧留出的余量，取描边宽度与视觉间距之和。 */
    const val TEXT_SIDE_PADDING = 56f

    /** 文字为适应气泡宽度可以缩到的下限比例。 */
    const val TEXT_SCALE_FLOOR = 0.45f

    /** 字号对应的字重，600 用于标签，800 用于金额与时段。 */
    const val LABEL_WEIGHT = 600
    const val AMOUNT_WEIGHT = 800
    const val HINT_WEIGHT = 400

    val TEXT_COLOR = 0xFF536BA9.toInt()
    val HINT_COLOR = 0xFF9FB0D9.toInt()
    val PEAK_COLOR = 0xFFE0433F.toInt()
    val VALLEY_COLOR = 0xFF2FA24C.toInt()
}
