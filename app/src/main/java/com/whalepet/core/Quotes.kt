package com.whalepet.core

import kotlin.random.Random

/** 气泡中的一行文字，空位用 null 表示。 */
class QuoteLine(
    val text: String,
    val style: QuoteStyle,
    val color: Int? = null,
    val wrap: Boolean = false
)

/** 台词行的样式档，字号与字重由样式档决定。 */
enum class QuoteStyle { LABEL, AMOUNT, PERIOD, HINT }

/** 生成台词所需的即时数据，提示行文本由内容入口格式化后传入。 */
class QuoteContext(
    val isPeak: Boolean,
    val usageHint: String?,
    val peakText: PeakText
)

class QuoteBundle(val lines: List<QuoteLine?>, val gif: Boolean = false)

/**
 * 随机台词库。
 *
 * 每组台词带权重，抽取时按权重累加取样，与余额气泡共用同一套三行结构。
 */
object Quotes {

    private class Group(val weight: Int, val build: (QuoteContext) -> QuoteBundle)

    private val SILLY_LINES = arrayOf(
        "不知道用户有什么用，先赶走吧~",
        "我...我...我也要挣钱吗？",
        "我去吃饭啦，测完叫我",
        "压力一只蓝色大肥鱼？！",
        "DeepSleep...",
        "坏了...用户彻底怒了！"
    )

    private val TEASE_LINES = arrayOf(
        "你目录里的dsh是什么...大烧货吗...?",
        "恭喜你实现token自由！token全跑了！",
        "真当我是便宜货啊..."
    )

    private val GROUPS = listOf(
        Group(45) { context ->
            QuoteBundle(
                listOf(
                    QuoteLine("当前时间段为:", QuoteStyle.LABEL),
                    QuoteLine(
                        text = Pricing.peakText(context.isPeak, context.peakText),
                        style = QuoteStyle.PERIOD,
                        color = if (context.isPeak) PetGeometry.PEAK_COLOR else PetGeometry.VALLEY_COLOR
                    ),
                    context.usageHint?.let { QuoteLine(it, QuoteStyle.HINT) }
                )
            )
        },
        Group(7) { QuoteBundle(centered(QuoteStyle.AMOUNT, pickOne("好模型... ↓", "好女孩...↓"))) },
        Group(7) { QuoteBundle(centered(QuoteStyle.LABEL, pickOne(*SILLY_LINES), wrap = true)) },
        Group(10) { QuoteBundle(emptyList(), gif = true) },
        Group(3) { QuoteBundle(centered(QuoteStyle.LABEL, pickOne(*TEASE_LINES), wrap = true)) },
        Group(1) { QuoteBundle(centered(QuoteStyle.AMOUNT, "哦鲸鲸... ")) }
    )

    /** 含时段与今日用量的台词组位于列表首位。 */
    private const val PERIOD_GROUP_COUNT = 1

    private val GIF_FALLBACK_LINES = arrayOf(
        "gif 加载失败了...",
        "今天没有动图给你看~",
        "呜呜 动图不见了..."
    )

    /** 动图不可用时替换为文字台词，避免出现空白气泡。 */
    fun gifFallback(): List<QuoteLine?> {
        return centered(QuoteStyle.LABEL, pickOne(*GIF_FALLBACK_LINES), wrap = true)
    }

    /**
     * 按权重抽取台词组。
     *
     * 隐藏余额时跳过含时段与用量的那一组，保证气泡内不出现任何余额相关内容。
     */
    fun pickRandom(context: QuoteContext, skipPeriodGroup: Boolean = false): QuoteBundle {
        val pool = if (skipPeriodGroup) GROUPS.drop(PERIOD_GROUP_COUNT) else GROUPS
        var remaining = Random.nextDouble() * pool.sumOf { it.weight }
        for (group in pool) {
            remaining -= group.weight
            if (remaining < 0.0) return group.build(context)
        }
        return pool.last().build(context)
    }

    private fun pickOne(vararg options: String): String = options[Random.nextInt(options.size)]

    private fun centered(style: QuoteStyle, text: String, wrap: Boolean = false): List<QuoteLine?> {
        return listOf(null, QuoteLine(text, style, wrap = wrap), null)
    }
}
