package com.whalepet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 气泡内容的取值规则。
 *
 * 覆盖余额、自定义文本、隐藏余额与消耗四路，以及用量提示行的取值与时段台词的同步。
 */
class BubbleContentTest {

    private val content = BubbleContent(FakeTexts())

    private fun snapshot(
        status: PetState.Status = PetState.Status.READY,
        balance: Double? = 100.0,
        currency: String = "CNY",
        todayUsage: Double? = 5.0,
        message: String? = null,
        isPeak: Boolean = false
    ) = PetState.Snapshot(
        status = status,
        balance = balance,
        currency = currency,
        todayUsage = todayUsage,
        updatedAt = 0L,
        message = message,
        isPeak = isPeak,
        stale = false,
        delta = null
    )

    private fun settings(
        hideBalance: Boolean = false,
        customEnabled: Boolean = false,
        customBalance: String = "",
        customUsage: String = "",
        showUsage: Boolean = true
    ) = BubbleContent.Settings(
        hideBalance = hideBalance,
        customEnabled = customEnabled,
        customBalance = customBalance,
        customUsage = customUsage,
        showUsage = showUsage,
        peakText = PeakText.DEFAULT
    )

    @Test
    fun balanceRowsUseFormattedValues() {
        val plan = content.plan(snapshot(), settings(), cost = null, gifAvailable = true)
        assertEquals(BubbleMode.BALANCE, plan.mode)
        assertEquals("余额", plan.lines[0]?.text)
        assertEquals("¥ 100.00", plan.lines[1]?.text)
        assertEquals("今日已用 ¥ 5.00", plan.lines[2]?.text)
    }

    @Test
    fun balanceOverrideReplacesShownAmount() {
        val display = content.display(snapshot(), settings(), balanceOverride = 42.5)
        assertEquals("¥ 42.50", display.balance)
    }

    @Test
    fun customNumericTextIsFormattedWithCurrency() {
        val configured = settings(
            customEnabled = true,
            customBalance = "88.8",
            customUsage = "6.6"
        )
        val display = content.display(snapshot(), configured)
        assertEquals("¥ 88.80", display.balance)
        assertEquals("今日已用 ¥ 6.60", display.usageHint)
    }

    @Test
    fun customPlainTextIsKeptVerbatim() {
        val configured = settings(
            customEnabled = true,
            customBalance = "很多钱",
            customUsage = "没花"
        )
        val plan = content.plan(snapshot(), configured, cost = null, gifAvailable = true)
        assertEquals("很多钱", plan.lines[1]?.text)
        assertEquals("今日已用 没花", plan.lines[2]?.text)
    }

    @Test
    fun usageRowIsHiddenWhenDisabled() {
        val display = content.display(snapshot(), settings(showUsage = false))
        assertNull(display.usageHint)
        val plan = content.plan(
            snapshot(),
            settings(showUsage = false),
            cost = null,
            gifAvailable = true
        )
        assertNull(plan.lines[2])
    }

    @Test
    fun balanceContentIsHiddenBehindQuotesWhenRequested() {
        val plan = content.plan(
            snapshot(),
            settings(hideBalance = true),
            cost = null,
            gifAvailable = true
        )
        assertEquals(BubbleMode.QUOTE, plan.mode)
    }

    @Test
    fun costBubbleShowsRedPositiveAmount() {
        val plan = content.plan(snapshot(), settings(), cost = -2.5, gifAvailable = true)
        assertEquals(BubbleMode.COST, plan.mode)
        assertEquals("上一轮对话消耗:", plan.lines[0]?.text)
        assertEquals("¥ 2.50", plan.lines[1]?.text)
        assertEquals(PetGeometry.PEAK_COLOR, plan.lines[1]?.color)
        assertNull(plan.lines[2])
    }

    @Test
    fun errorStateShowsMessageInUsageRow() {
        val failed = content.display(
            snapshot(status = PetState.Status.ERROR, balance = null, message = "接口返回 500"),
            settings()
        )
        assertEquals("--", failed.balance)
        assertEquals("接口返回 500", failed.usageHint)
    }

    @Test
    fun loadingStateUsesPlaceholders() {
        val plan = content.plan(
            snapshot(status = PetState.Status.IDLE, balance = null, todayUsage = null),
            settings(),
            cost = null,
            gifAvailable = true
        )
        assertEquals("…", plan.lines[1]?.text)
        assertEquals("加载中…", plan.lines[2]?.text)
    }

    @Test
    fun periodQuoteCarriesTheSameUsageTextAsTheBalanceBubble() {
        val configured = settings(
            customEnabled = true,
            customBalance = "88.8",
            customUsage = "6.6"
        )
        val display = content.display(snapshot(), configured)
        var hits = 0
        repeat(300) {
            val plan = content.quotePlan(display, skipPeriodGroup = false, gifAvailable = true)
            if (plan.lines.getOrNull(0)?.text == "当前时间段为:") {
                hits++
                assertEquals(display.usageHint, plan.lines[2]?.text)
            }
        }
        assertTrue(hits > 0)
    }

    @Test
    fun hidingBalanceSkipsThePeriodGroup() {
        val display = content.display(snapshot(), settings())
        repeat(300) {
            val plan = content.quotePlan(display, skipPeriodGroup = true, gifAvailable = false)
            assertTrue(plan.lines.getOrNull(0)?.text != "当前时间段为:")
        }
    }

    /** 气泡文案的替身，固定文案便于断言。 */
    private class FakeTexts : BubbleTexts {
        override val balanceLabel: String = "余额"
        override val costLabel: String = "上一轮对话消耗:"
        override val loading: String = "加载中…"
        override val failed: String = "获取失败"
        override fun todayUsage(value: String): String = "今日已用 $value"
    }
}
