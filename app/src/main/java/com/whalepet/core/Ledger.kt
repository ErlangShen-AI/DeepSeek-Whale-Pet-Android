package com.whalepet.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 余额差值记账。
 *
 * 每次观测到余额下降就把差值累加到当天用量，跨天归档并在观测币种变化时只重置基准，
 * 避免多币种切换被误记为消费。
 */
class Ledger internal constructor(private val file: File) {

    class Entry(val date: String, val usage: Double)

    class Record(
        var date: String,
        var lastBalance: Double,
        var lastCurrency: String,
        var todayUsage: Double,
        val history: MutableList<Entry>
    )

    /** 观测一次余额并返回更新后的账本，读写文件放在 IO 线程。 */
    suspend fun observe(balance: Double, currency: String, nowMillis: Long): Record =
        withContext(Dispatchers.IO) { observeBlocking(balance, currency, nowMillis) }

    private fun observeBlocking(balance: Double, currency: String, nowMillis: Long): Record {
        val record = read()
        val today = dayKey(nowMillis)

        if (record.lastCurrency.isNotEmpty() && record.lastCurrency != currency) {
            record.lastBalance = balance
            record.lastCurrency = currency
            if (record.date.isEmpty()) record.date = today
            persist(record)
            return record
        }

        if (record.date != today) {
            if (record.date.isNotEmpty() && record.todayUsage > 0.0) {
                record.history.add(0, Entry(record.date, record.todayUsage))
                while (record.history.size > HISTORY_LIMIT) {
                    record.history.removeAt(record.history.size - 1)
                }
            }
            record.date = today
            record.todayUsage = 0.0
            record.lastBalance = balance
            record.lastCurrency = currency
            persist(record)
            return record
        }

        if (balance < record.lastBalance) {
            record.todayUsage += record.lastBalance - balance
        }
        record.lastBalance = balance
        record.lastCurrency = currency
        persist(record)
        return record
    }

    private fun read(): Record {
        val record = Record("", 0.0, "", 0.0, mutableListOf())
        if (!file.exists()) return record
        return try {
            val root = JSONObject(file.readText())
            val history = mutableListOf<Entry>()
            val array = root.optJSONArray(KEY_HISTORY)
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val date = item.optString(KEY_DATE, "")
                    if (date.isEmpty()) continue
                    history.add(Entry(date, item.optDouble(KEY_USAGE, 0.0)))
                }
            }
            Record(
                date = root.optString(KEY_DATE, ""),
                lastBalance = root.optDouble(KEY_LAST_BALANCE, 0.0),
                lastCurrency = root.optString(KEY_LAST_CURRENCY, ""),
                todayUsage = root.optDouble(KEY_TODAY_USAGE, 0.0),
                history = history
            )
        } catch (error: Exception) {
            Record(dayKey(System.currentTimeMillis()), 0.0, "", 0.0, mutableListOf())
        }
    }

    private fun persist(record: Record) {
        val root = JSONObject()
        root.put(KEY_DATE, record.date)
        root.put(KEY_LAST_BALANCE, record.lastBalance)
        root.put(KEY_LAST_CURRENCY, record.lastCurrency)
        root.put(KEY_TODAY_USAGE, record.todayUsage)
        val array = JSONArray()
        for (entry in record.history) {
            val item = JSONObject()
            item.put(KEY_DATE, entry.date)
            item.put(KEY_USAGE, entry.usage)
            array.put(item)
        }
        root.put(KEY_HISTORY, array)
        file.writeText(root.toString())
    }

    private fun dayKey(nowMillis: Long): String {
        return Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    companion object {

        private const val FILE_NAME = "usage_ledger.json"
        private const val HISTORY_LIMIT = 30

        private const val KEY_DATE = "date"
        private const val KEY_LAST_BALANCE = "lastBalance"
        private const val KEY_LAST_CURRENCY = "lastCurrency"
        private const val KEY_TODAY_USAGE = "todayUsage"
        private const val KEY_HISTORY = "history"
        private const val KEY_USAGE = "usage"

        @Volatile
        private var instance: Ledger? = null

        fun get(context: Context): Ledger {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: Ledger(File(context.applicationContext.filesDir, FILE_NAME))
                    .also { instance = it }
            }
        }

        /** 当天零点的毫秒时间戳，用于按小时分桶的用量查询。 */
        fun startOfToday(nowMillis: Long): Long {
            val zone = ZoneId.systemDefault()
            val date: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            return date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
