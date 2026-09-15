package com.whalepet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 峰谷定价的换算与价格表选择。
 *
 * 价格表按模型名关键字命中，命中不到时回退基础价；cost 按 token 分桶换算，token 数除以百万后乘单价。
 */
class PricingTest {

    @Test
    fun costUsesBucketPriceByPeak() {
        val price = TokenPrice(
            cacheHit = doubleArrayOf(0.05, 0.10),
            cacheMiss = doubleArrayOf(1.5, 3.0),
            output = doubleArrayOf(4.5, 9.0)
        )

        assertEquals(
            0.05,
            Pricing.cost(price, peak = false, cacheHitTokens = 1_000_000, cacheMissTokens = 0, outputTokens = 0),
            1e-9
        )
        assertEquals(
            0.10,
            Pricing.cost(price, peak = true, cacheHitTokens = 1_000_000, cacheMissTokens = 0, outputTokens = 0),
            1e-9
        )
        assertEquals(
            1.5,
            Pricing.cost(price, peak = false, cacheHitTokens = 0, cacheMissTokens = 1_000_000, outputTokens = 0),
            1e-9
        )
        assertEquals(
            9.0,
            Pricing.cost(price, peak = true, cacheHitTokens = 0, cacheMissTokens = 0, outputTokens = 1_000_000),
            1e-9
        )
    }

    @Test
    fun costAddsAllBuckets() {
        val price = TokenPrice(
            cacheHit = doubleArrayOf(0.05, 0.10),
            cacheMiss = doubleArrayOf(1.5, 3.0),
            output = doubleArrayOf(4.5, 9.0)
        )

        assertEquals(
            6.05,
            Pricing.cost(
                price,
                peak = false,
                cacheHitTokens = 1_000_000,
                cacheMissTokens = 1_000_000,
                outputTokens = 1_000_000
            ),
            1e-9
        )
    }

    @Test
    fun priceTableReusesSameInstancePerModel() {
        assertSame(Pricing.priceOf("deepseek-chat"), Pricing.priceOf("deepseek-reasoner"))
        assertSame(Pricing.priceOf("deepseek-v4-pro"), Pricing.priceOf("DEEPSEEK-V4-PRO"))
    }

    @Test
    fun unknownModelFallsBackToBasePrice() {
        assertSame(Pricing.priceOf("deepseek-chat"), Pricing.priceOf("unknown-model"))
    }

    @Test
    fun peakTextFollowsSelectedStyle() {
        assertEquals("高峰时段", Pricing.peakText(true, PeakText.DEFAULT))
        assertEquals("空闲时段", Pricing.peakText(false, PeakText.DEFAULT))
        assertEquals("梁文峰", Pricing.peakText(true, PeakText.LIANGWEN))
        assertEquals("梁文谷", Pricing.peakText(false, PeakText.LIANGWEN))
        assertEquals("!?峰峰?!", Pricing.peakText(true, PeakText.QIANGQIANG))
        assertEquals("!?谷谷?!", Pricing.peakText(false, PeakText.QIANGQIANG))
    }
}

/**
 * 金额与币种的展示格式。
 */
class MoneyTest {

    @Test
    fun cnyUsesPrefixSymbol() {
        assertEquals("¥ 1234.50", Money.format(1234.5, "CNY"))
        assertEquals("¥ 0.00", Money.format(0.0, "CNY"))
    }

    @Test
    fun otherCurrencyUsesSuffixCode() {
        assertEquals("12.00 USD", Money.format(12.0, "USD"))
    }

    @Test
    fun unknownAmountShowsPlaceholder() {
        assertEquals(Money.PLACEHOLDER, Money.format(null, "CNY"))
        assertEquals(Money.PLACEHOLDER, Money.format(Double.NaN, "CNY"))
    }
}
