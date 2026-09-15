package com.whalepet.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 一次余额观测的结果。 */
class BalanceSnapshot(
    val amount: Double,
    val currency: String,
    val observedAt: Long
)

/** 携带 HTTP 状态码的请求失败，用于区分可重试与不可重试的错误。 */
class HttpStatusException(val status: Int) : IOException("HTTP $status")

/**
 * DeepSeek 余额与用量接口。
 *
 * 余额来自官方接口，用量来自平台用量接口，两者都以 JSON 文本返回后在此解析成金额。
 */
object DeepSeekApi {

    private const val BALANCE_URL = "https://api.deepseek.com/user/balance"
    private const val USAGE_URL = "https://platform.deepseek.com/api/v0/usage/by_api_key/amount"

    private const val BALANCE_TIMEOUT_MS = 20_000
    private const val USAGE_TIMEOUT_MS = 15_000
    private const val RETRY_DELAY_MS = 500L

    suspend fun fetchBalance(key: String): Result<BalanceSnapshot> = withContext(Dispatchers.IO) {
        var lastError: Throwable = IOException("balance request failed")
        for (attempt in 0..1) {
            try {
                val body = request(BALANCE_URL, mapOf("Authorization" to "Bearer $key"), BALANCE_TIMEOUT_MS)
                return@withContext Result.success(parseBalance(body))
            } catch (error: HttpStatusException) {
                if (error.status in 400..499) return@withContext Result.failure(error)
                lastError = error
            } catch (error: IOException) {
                lastError = error
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return@withContext Result.failure(error)
            }
            if (attempt == 0) delay(RETRY_DELAY_MS)
        }
        Result.failure(lastError)
    }

    suspend fun fetchTodayUsage(token: String, startMillis: Long): Result<Double> =
        withContext(Dispatchers.IO) {
            try {
                val start = startMillis / 1000
                val end = start + 86_400
                val url = "$USAGE_URL?start=$start&end=$end&tz=28800"
                val body = request(url, mapOf("Authorization" to "Bearer $token"), USAGE_TIMEOUT_MS)
                Result.success(parseUsage(body))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    /**
     * 多币种余额的选取顺序：优先币种为 CNY 且余额大于零的条目，其次任意非零条目，
     * 再回退到 CNY 条目，最后取第一条，避免接口返回顺序不固定造成展示抖动。
     */
    private fun parseBalance(body: String): BalanceSnapshot {
        val root = JSONObject(body)
        val infos = root.optJSONArray("balance_infos") ?: JSONArray()
        val entries = mutableListOf<Pair<String, Double>>()
        for (index in 0 until infos.length()) {
            val item = infos.optJSONObject(index) ?: continue
            val currency = item.optString("currency", "")
            if (currency.isEmpty()) continue
            val amount = item.optString("total_balance", "").toDoubleOrNull() ?: continue
            if (!amount.isFinite()) continue
            entries.add(currency to amount)
        }
        if (entries.isEmpty()) throw IOException("balance_infos empty")
        val picked = entries.firstOrNull { it.first.equals("CNY", true) && it.second > 0.0 }
            ?: entries.firstOrNull { it.second > 0.0 }
            ?: entries.firstOrNull { it.first.equals("CNY", true) }
            ?: entries.first()
        return BalanceSnapshot(picked.second, picked.first, System.currentTimeMillis())
    }

    /** 用量接口只返回 token 分桶，按桶所在小时的峰谷状态换算成金额后求和。 */
    private fun parseUsage(body: String): Double {
        val root = JSONObject(body)
        val bizData = root.optJSONObject("data")?.optJSONObject("biz_data") ?: return 0.0
        val series = bizData.optJSONArray("series") ?: return 0.0
        var total = 0.0
        for (index in 0 until series.length()) {
            val model = series.optJSONObject(index) ?: continue
            val price = Pricing.priceOf(model.optString("model", ""))
            val buckets = model.optJSONArray("buckets") ?: continue
            for (bucketIndex in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(bucketIndex) ?: continue
                val usage = bucket.optJSONObject("usage") ?: continue
                val peak = Pricing.isPeak(bucket.optLong("time", 0L) * 1000L)
                total += Pricing.cost(
                    price = price,
                    peak = peak,
                    cacheHitTokens = usage.optLong("PROMPT_CACHE_HIT_TOKEN", 0L),
                    cacheMissTokens = usage.optLong("PROMPT_CACHE_MISS_TOKEN", 0L),
                    outputTokens = usage.optLong("RESPONSE_TOKEN", 0L)
                )
            }
        }
        return total
    }

    private fun request(url: String, headers: Map<String, String>, timeoutMs: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Accept", "application/json")
            for ((name, value) in headers) {
                connection.setRequestProperty(name, value)
            }
            val status = connection.responseCode
            if (status !in 200..299) throw HttpStatusException(status)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
