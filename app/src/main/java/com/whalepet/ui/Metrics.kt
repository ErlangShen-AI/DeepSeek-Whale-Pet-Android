package com.whalepet.ui

import android.content.Context
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * 尺寸与字号的唯一来源。
 *
 * 间距、圆角、行高都由 unit 派生，unit 取屏幕短边的比例并限定上下限，
 * 因此不同尺寸与密度的设备共享同一套比例，不需要为每种屏幕单独定义数值。
 */
class Metrics(context: Context) {

    private val resources = context.resources
    private val displayMetrics = resources.displayMetrics
    private val configuration = resources.configuration

    private val shortestDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()

    /** 基准间距单位，单位为像素。 */
    val unit: Float = dp(shortestDp * UNIT_FRACTION).coerceIn(dp(UNIT_MIN_DP), dp(UNIT_MAX_DP))

    /** 文字随屏幕放大的系数，避免大屏上文字相对过小。 */
    private val textFactor =
        (shortestDp / REFERENCE_WIDTH_DP).coerceIn(TEXT_FACTOR_MIN, TEXT_FACTOR_MAX)

    fun dp(value: Float): Float = value * displayMetrics.density

    fun uf(multiplier: Float): Float = unit * multiplier

    fun u(multiplier: Float): Int = (unit * multiplier).roundToInt()

    /** 字号，单位为像素，同时响应屏幕尺寸与系统字号设置。 */
    fun text(role: TextRole): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            role.size * textFactor,
            displayMetrics
        )
    }

    fun lineHeight(role: TextRole): Float = text(role) * role.lineHeight

    companion object {
        private const val UNIT_FRACTION = 0.021f
        private const val UNIT_MIN_DP = 5.5f
        private const val UNIT_MAX_DP = 13f
        private const val REFERENCE_WIDTH_DP = 390f
        private const val TEXT_FACTOR_MIN = 0.92f
        private const val TEXT_FACTOR_MAX = 1.18f
    }
}
