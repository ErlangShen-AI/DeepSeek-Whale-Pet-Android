package com.whalepet.core

import kotlin.math.PI

/**
 * 动效令牌，集中描述弹簧参数与时长。
 *
 * 弹簧用「响应时间」与「阻尼比」表达：响应时间越短越快，阻尼比 1.0 为临界阻尼不产生过冲，
 * 小于 1.0 才带回弹。产生过冲的场合只用在手势带出速度之后。
 */
object Motion {

    /** 由响应时间换算弹簧刚度，角频率与响应时间成倒数关系。 */
    fun stiffnessOf(responseSeconds: Float): Float {
        val omega = 2f * PI.toFloat() / responseSeconds
        return omega * omega
    }

    const val FREE_RESPONSE = 0.4f
    const val FREE_DAMPING = 1.0f

    /** 位置结算时长。 */
    const val SNAP_SECONDS = 0.16f

    /** 缩放过渡时长，比位置过渡略长，避免尺寸跳变。 */
    const val SIZE_SECONDS = 0.28f

    /** 按压形变时长。 */
    const val PRESS_SECONDS = 0.22f

    /** 气泡三个图形的淡入时长与各自错峰延迟。 */
    const val SHAPE_SECONDS = 0.2f
    const val SHAPE_DELAY_MAIN_OPEN = 0.26f
    const val SHAPE_DELAY_ONE_OPEN = 0.13f
    const val SHAPE_DELAY_TWO_OPEN = 0f
    const val SHAPE_DELAY_MAIN_CLOSE = 0.1f
    const val SHAPE_DELAY_ONE_CLOSE = 0.2f
    const val SHAPE_DELAY_TWO_CLOSE = 0.3f

    /** 气泡图形缩放的两端。 */
    const val SHAPE_SCALE_FROM = 0.7f
    const val SHAPE_SCALE_TO = 1f

    const val FLIP_SECONDS = 0.3f
    const val BUBBLE_FADE_SECONDS = 0.16f
    const val TEXT_FADE_DELAY_SECONDS = 0.36f
    const val ROLL_SECONDS = 0.7f
    const val SWAP_FADE_SECONDS = 0.22f

    const val DRAG_THRESHOLD_SQUARED = 9f
}
