package com.whalepet.core

import java.util.Locale

/** 单个模型每百万 token 的价格，数组下标 0 为空闲时段价，1 为高峰时段价。 */
class TokenPrice(
    val cacheHit: DoubleArray,
    val cacheMiss: DoubleArray,
    val output: DoubleArray
)

/**
 * DeepSeek 峰谷定价与时段判定。
 *
 * 高峰时段为北京时间工作日 9:00-12:00 与 14:00-18:00，2026-08-23 起周末全天按空闲价。
 * 调整价格时只需修改此处的价格表与时段表。
 */
object Pricing {

    private const val IDLE = 0
    private const val PEAK = 1

    private const val DAY_MILLIS = 86_400_000L
    private const val HOUR_MILLIS = 3_600_000L

    /** 北京时间固定时区偏移，中国全境不使用夏令时。 */
    const val BEIJING_OFFSET_MILLIS = 8L * HOUR_MILLIS

    /** 北京时间 2026-08-23 00:00，此后周末全天按空闲价。 */
    const val WEEKEND_VALLEY_FROM_MILLIS = 1_787_414_400_000L

    private val PEAK_HOURS = listOf(9 to 12, 14 to 18)

    private val BASE_PRICE = TokenPrice(
        cacheHit = doubleArrayOf(0.05, 0.10),
        cacheMiss = doubleArrayOf(1.5, 3.0),
        output = doubleArrayOf(4.5, 9.0)
    )

    private val PRO_PRICE = TokenPrice(
        cacheHit = doubleArrayOf(0.15, 0.30),
        cacheMiss = doubleArrayOf(4.5, 9.0),
        output = doubleArrayOf(13.5, 27.0)
    )

    /** 模型名包含左侧关键字时套用对应价格，未命中时回退基础价格。 */
    private val TABLE = listOf(
        "deepseek-v4-flash-vision-exp" to BASE_PRICE,
        "deepseek-v4-flash" to BASE_PRICE,
        "deepseek-v4-pro" to PRO_PRICE,
        "deepseek-chat" to BASE_PRICE,
        "deepseek-reasoner" to BASE_PRICE
    )

    fun priceOf(model: String): TokenPrice {
        val name = model.lowercase(Locale.ROOT)
        for ((keyword, price) in TABLE) {
            if (name.contains(keyword)) return price
        }
        return BASE_PRICE
    }

    /** 以北京日历判定给定时刻是否处于高峰时段。 */
    fun isPeak(timeMillis: Long): Boolean {
        val beijing = timeMillis + BEIJING_OFFSET_MILLIS
        if (timeMillis >= WEEKEND_VALLEY_FROM_MILLIS) {
            val weekday = ((Math.floorDiv(beijing, DAY_MILLIS) + 4L) % 7L).toInt()
            if (weekday == 0 || weekday == 6) return false
        }
        val hour = ((beijing % DAY_MILLIS) / HOUR_MILLIS).toInt()
        for ((start, end) in PEAK_HOURS) {
            if (hour >= start && hour < end) return true
        }
        return false
    }

    /** 按 token 分桶金额换算，token 数除以百万后乘以对应单价。 */
    fun cost(
        price: TokenPrice,
        peak: Boolean,
        cacheHitTokens: Long,
        cacheMissTokens: Long,
        outputTokens: Long
    ): Double {
        val index = if (peak) PEAK else IDLE
        val million = 1_000_000.0
        return cacheHitTokens / million * price.cacheHit[index] +
            cacheMissTokens / million * price.cacheMiss[index] +
            outputTokens / million * price.output[index]
    }

    /** 展示用的时段名称，三种文案风格对应不同的说法。 */
    fun peakText(peak: Boolean, style: PeakText): String {
        return when (style) {
            PeakText.DEFAULT -> if (peak) "高峰时段" else "空闲时段"
            PeakText.LIANGWEN -> if (peak) "梁文峰" else "梁文谷"
            PeakText.QIANGQIANG -> if (peak) "!?峰峰?!" else "!?谷谷?!"
        }
    }
}
