package com.whalepet.core

import android.content.Context
import com.whalepet.R

/**
 * 气泡文案的来源。
 *
 * 取值规则与 Android 资源解耦，构造时注入的实现决定文案从系统资源还是测试替身读取。
 */
interface BubbleTexts {

    val balanceLabel: String
    val costLabel: String
    val loading: String
    val failed: String

    fun todayUsage(value: String): String
}

/** 从应用资源读取气泡文案。 */
class AndroidBubbleTexts(private val context: Context) : BubbleTexts {

    override val balanceLabel: String
        get() = context.getString(R.string.bubble_balance_label)

    override val costLabel: String
        get() = context.getString(R.string.bubble_cost_label)

    override val loading: String
        get() = context.getString(R.string.bubble_loading)

    override val failed: String
        get() = context.getString(R.string.status_failed_retry)

    override fun todayUsage(value: String): String =
        context.getString(R.string.bubble_today_usage, value)
}
