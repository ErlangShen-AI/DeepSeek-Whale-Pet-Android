package com.whalepet.pet

import android.os.Handler
import com.whalepet.core.BubbleContent
import com.whalepet.core.BubbleMode
import com.whalepet.core.BubblePlan
import com.whalepet.core.Motion
import com.whalepet.core.PetState
import com.whalepet.core.Prefs
import com.whalepet.core.QuoteLine

/**
 * 气泡内容的呈现状态机。
 *
 * 决定这一屏显示余额、消耗还是台词，负责自动收起与数字滚动的排期，
 * 并记录余额与币种的上一份取值用于判断「是否变化」。
 * 窗口增删、内容推送与逐帧推进通过宿主接口交回调用方，因此这里不持有窗口与视图。
 */
class BubblePresenter(
    private val prefs: Prefs,
    private val state: PetState,
    private val content: BubbleContent,
    private val handler: Handler,
    private val host: Host
) {

    /** 呈现状态机需要调用方提供的窗口与视图动作。 */
    interface Host {
        fun ensureBubbleWindow()
        fun setBubbleTouchable(touchable: Boolean)
        fun scheduleBubbleRemoval()
        fun cancelBubbleRemoval()
        fun bubbleVisible(): Boolean
        fun gifAvailable(): Boolean
        fun prefersReducedMotion(): Boolean
        fun openBubble()
        fun closeBubble()
        fun pushContent(lines: List<QuoteLine?>, gif: Boolean, animated: Boolean)
        fun requestFrames()
    }

    private var settings: BubbleContent.Settings = content.settingsOf(prefs)
    private var mode = BubbleMode.BALANCE
    private var costDelta: Double? = null
    private var lastBalance: Double? = null
    private var lastCurrency: String? = null
    private var manualRefresh = false

    private val rollTween = Tween()
    private var rollFrom = 0.0
    private var rollTo = 0.0
    private var rollValue: Double? = null

    private val hideRunnable = Runnable { hideBubble() }
    private val rollRunnable = Runnable { startRoll() }

    /** 开关变化后刷新快照，返回是否影响内容取值。 */
    fun refreshSettings(): Boolean {
        val updated = content.settingsOf(prefs)
        val changed = updated != settings
        settings = updated
        return changed
    }

    /** 余额快照到达后的呈现决策。 */
    fun onSnapshot(snapshot: PetState.Snapshot) {
        val previous = lastBalance
        val previousCurrency = lastCurrency
        val manual = manualRefresh
        manualRefresh = false

        val currencyChanged = previousCurrency != null && previousCurrency != snapshot.currency
        val changed = previous != null && snapshot.balance != null &&
            (snapshot.balance != previous || currencyChanged)
        val delta = snapshot.delta

        lastBalance = snapshot.balance
        lastCurrency = snapshot.currency

        if (settings.hideBalance) {
            rollValue = null
            if (changed) present(plan(), animated = true)
            return
        }

        if (mode == BubbleMode.COST) return

        if (!changed || currencyChanged) {
            rollValue = snapshot.balance
            if (mode == BubbleMode.BALANCE && host.bubbleVisible()) installContent()
            return
        }

        if (manual) {
            rollValue = snapshot.balance
            if (host.bubbleVisible()) installContent()
            return
        }

        if (delta != null && delta < 0.0) {
            showCost(delta)
            return
        }

        rollValue = previous
        present(plan(balanceOverride = previous), animated = false)
        scheduleRoll()
    }

    /** 点击鲸鱼：手动刷新并把内容切回余额或台词。 */
    fun onWhaleTap() {
        manualRefresh = true
        state.refresh(manual = true)
        costDelta = null
        if (settings.hideBalance) {
            present(plan(), animated = true)
            return
        }
        present(plan(), animated = false)
    }

    /** 点击气泡：消耗与台词直接收起，余额内容切到随机台词。 */
    fun onBubbleTap() {
        when (mode) {
            BubbleMode.COST, BubbleMode.QUOTE -> hideBubble()
            BubbleMode.BALANCE -> present(quotePlan(), animated = true)
        }
    }

    /** 每帧推进数字滚动，返回是否仍在滚动。 */
    fun advanceRoll(deltaSeconds: Float): Boolean {
        if (!rollTween.isRunning) return false
        val running = rollTween.advance(deltaSeconds)
        rollValue = rollFrom + (rollTo - rollFrom) * rollTween.value
        installContent()
        return running
    }

    /** 屏幕尺寸变化后按当前余额重算一次内容。 */
    fun refreshVisibleContent() {
        if (mode == BubbleMode.BALANCE && host.bubbleVisible()) installContent()
    }

    /** 取消自动收起与滚动排期，用于服务停止或配置改动。 */
    fun cancelPending() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(rollRunnable)
    }

    /** 取消自动收起，自动关闭时间设为 0 秒时使用。 */
    fun cancelAutoHide() {
        handler.removeCallbacks(hideRunnable)
    }

    /** 当前内容：消耗气泡优先，其次只显示台词，再其次自定义或真实余额。 */
    private fun plan(cost: Double? = costDelta, balanceOverride: Double? = null): BubblePlan {
        return content.plan(
            snapshot = state.snapshot.value,
            settings = settings,
            cost = cost,
            gifAvailable = host.gifAvailable(),
            balanceOverride = balanceOverride
        )
    }

    /** 随机台词内容，隐藏余额时跳过含时段与用量的那一组。 */
    private fun quotePlan(): BubblePlan {
        val display = content.display(state.snapshot.value, settings)
        return content.quotePlan(
            display = display,
            skipPeriodGroup = settings.hideBalance,
            gifAvailable = host.gifAvailable()
        )
    }

    /** 显示一份内容，气泡窗口不存在时先挂上。 */
    private fun present(plan: BubblePlan, animated: Boolean) {
        if (!prefs.bubbleEnabled) return
        mode = plan.mode
        handler.removeCallbacks(rollRunnable)
        host.cancelBubbleRemoval()
        host.ensureBubbleWindow()
        host.setBubbleTouchable(true)
        val swap = animated && host.bubbleVisible()
        host.openBubble()
        host.pushContent(plan.lines, plan.gif, swap)
        armAutoHide()
    }

    /** 消耗气泡：金额显示为红色正数，数字滚动同时停下。 */
    private fun showCost(delta: Double) {
        costDelta = delta
        rollValue = null
        present(plan(), animated = false)
    }

    private fun hideBubble() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(rollRunnable)
        costDelta = null
        mode = BubbleMode.BALANCE
        host.closeBubble()
        host.setBubbleTouchable(false)
        host.scheduleBubbleRemoval()
    }

    private fun armAutoHide() {
        handler.removeCallbacks(hideRunnable)
        val seconds = prefs.bubbleSeconds
        if (seconds > 0) {
            handler.postDelayed(hideRunnable, seconds * 1000L)
        }
    }

    private fun scheduleRoll() {
        handler.removeCallbacks(rollRunnable)
        if (lastBalance == null) return
        handler.postDelayed(rollRunnable, ROLL_DELAY_MS)
    }

    /** 数字滚动逐帧走同一条内容管线。 */
    private fun startRoll() {
        val target = lastBalance ?: return
        val from = rollValue ?: target
        if (from == target || host.prefersReducedMotion()) {
            rollValue = target
            installContent()
            return
        }
        rollFrom = from
        rollTo = target
        rollValue = from
        rollTween.start(0f, 1f, Motion.ROLL_SECONDS, 0f, Easing.EASE_OUT_CUBIC)
        host.requestFrames()
    }

    /** 把当前内容交给视图并请求推帧。 */
    private fun installContent() {
        val current = plan(balanceOverride = rollValue)
        host.pushContent(current.lines, current.gif, animated = false)
    }

    companion object {
        private const val ROLL_DELAY_MS = 300L
    }
}
