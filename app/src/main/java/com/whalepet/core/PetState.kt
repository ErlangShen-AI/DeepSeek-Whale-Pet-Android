package com.whalepet.core

import android.content.Context
import com.whalepet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * 余额状态的唯一来源。
 *
 * 负责拉取余额、写入账本、解析今日用量，并把结果广播给界面与桌宠。
 * 瞬时网络失败会沿用最近一次成功值，只有明确的客户端错误才切换到失败状态。
 */
class PetState private constructor(private val context: Context) {

    enum class Status { IDLE, LOADING, READY, ERROR }

    data class Snapshot(
        val status: Status,
        val balance: Double?,
        val currency: String,
        val todayUsage: Double?,
        val updatedAt: Long,
        val message: String?,
        val isPeak: Boolean,
        val stale: Boolean,
        val delta: Double?
    )

    private val prefs = Prefs.get(context)
    private val ledger = Ledger.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _snapshot = MutableStateFlow(
        Snapshot(
            status = Status.IDLE,
            balance = null,
            currency = DEFAULT_CURRENCY,
            todayUsage = null,
            updatedAt = 0L,
            message = null,
            isPeak = Pricing.isPeak(System.currentTimeMillis()),
            stale = false,
            delta = null
        )
    )

    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var job: Job? = null
    private var lastSuccess: BalanceSnapshot? = null
    private var lastFetchAt = 0L
    private var previousBalance: Double? = null

    fun refresh(manual: Boolean) {
        if (job?.isActive == true) return

        val key = prefs.apiKey
        if (key.isEmpty()) {
            _snapshot.value = _snapshot.value.copy(
                status = Status.ERROR,
                message = context.getString(R.string.status_missing_key),
                stale = false
            )
            return
        }

        val cached = lastSuccess
        val now = System.currentTimeMillis()
        if (!manual && cached != null && now - lastFetchAt < CACHE_TTL_MS) return

        if (manual || _snapshot.value.balance == null) {
            _snapshot.value = _snapshot.value.copy(status = Status.LOADING, message = null)
        }

        job = scope.launch {
            val result = DeepSeekApi.fetchBalance(key)
            result.fold(
                onSuccess = { value -> onBalance(value) },
                onFailure = { error -> onFailure(error) }
            )
        }
    }

    /** 用量模式或令牌改变后丢弃缓存，下一次刷新重新取值。 */
    fun invalidate() {
        lastFetchAt = 0L
    }

    private suspend fun onBalance(value: BalanceSnapshot) {
        lastSuccess = value
        lastFetchAt = System.currentTimeMillis()
        val record = ledger.observe(value.amount, value.currency, value.observedAt)
        val todayUsage = resolveTodayUsage(value.observedAt, record.todayUsage)
        val delta = previousBalance?.let { value.amount - it }
        previousBalance = value.amount
        _snapshot.value = Snapshot(
            status = Status.READY,
            balance = value.amount,
            currency = value.currency,
            todayUsage = todayUsage,
            updatedAt = value.observedAt,
            message = null,
            isPeak = Pricing.isPeak(value.observedAt),
            stale = false,
            delta = delta
        )
    }

    private suspend fun resolveTodayUsage(observedAt: Long, ledgerUsage: Double): Double? {
        if (!prefs.showTodayUsage) return null
        if (prefs.usageMode != UsageMode.TOKEN) return ledgerUsage
        val token = prefs.platformToken
        if (token.isEmpty()) return ledgerUsage
        val start = Ledger.startOfToday(observedAt)
        return DeepSeekApi.fetchTodayUsage(token, start).getOrElse { ledgerUsage }
    }

    private fun onFailure(error: Throwable) {
        val cached = lastSuccess
        if (cached != null && isTransient(error)) {
            _snapshot.value = _snapshot.value.copy(
                status = Status.READY,
                stale = true,
                message = describe(error)
            )
            return
        }
        _snapshot.value = _snapshot.value.copy(
            status = Status.ERROR,
            stale = false,
            message = describe(error)
        )
    }

    private fun isTransient(error: Throwable): Boolean {
        return when (error) {
            is HttpStatusException -> error.status >= 500
            is IOException -> true
            else -> false
        }
    }

    private fun describe(error: Throwable): String {
        return when {
            error is HttpStatusException && error.status == 401 ->
                context.getString(R.string.status_key_invalid)
            error is HttpStatusException && error.status == 403 ->
                context.getString(R.string.status_forbidden)
            error is HttpStatusException ->
                context.getString(R.string.status_http, error.status)
            error is SocketTimeoutException ->
                context.getString(R.string.status_timeout)
            error is IOException ->
                context.getString(R.string.status_network)
            else ->
                context.getString(R.string.status_failed)
        }
    }

    companion object {

        private const val CACHE_TTL_MS = 25_000L
        private const val DEFAULT_CURRENCY = "CNY"

        @Volatile
        private var instance: PetState? = null

        fun get(context: Context): PetState {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: PetState(context.applicationContext).also { instance = it }
            }
        }
    }
}
