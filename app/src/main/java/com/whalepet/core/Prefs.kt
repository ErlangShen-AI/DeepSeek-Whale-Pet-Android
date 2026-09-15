package com.whalepet.core

import android.content.Context
import android.content.SharedPreferences

enum class UsageMode { LEDGER, TOKEN }

enum class SoundSet { DUCK, FX1, OFF }

enum class PeakText { DEFAULT, LIANGWEN, QIANGQIANG }

enum class SnapHorizontal { NONE, LEFT, RIGHT }

enum class SnapVertical { NONE, TOP, BOTTOM }

/**
 * 应用配置的读写入口。
 *
 * 写入前先与当前值比较，值未变化时不落盘也不广播，
 * 因此输入框逐字保存、滑块连续拖动都不会产生多余的刷新回调。
 */
class Prefs private constructor(context: Context) {

    fun interface Listener {
        fun onPrefsChanged()
    }

    private val store: SharedPreferences =
        context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    var apiKey: String
        get() = store.getString(KEY_API_KEY, "").orEmpty()
        set(value) = putString(KEY_API_KEY, value.trim(), apiKey)

    var platformToken: String
        get() = store.getString(KEY_PLATFORM_TOKEN, "").orEmpty()
        set(value) = putString(KEY_PLATFORM_TOKEN, value.trim(), platformToken)

    var usageMode: UsageMode
        get() = readEnum(KEY_USAGE_MODE, UsageMode.entries.toTypedArray(), UsageMode.LEDGER)
        set(value) {
            if (value == usageMode) return
            store.edit().putString(KEY_USAGE_MODE, value.name).apply()
            notifyChanged()
        }

    var peakText: PeakText
        get() = readEnum(KEY_PEAK_TEXT, PeakText.entries.toTypedArray(), PeakText.DEFAULT)
        set(value) {
            if (value == peakText) return
            store.edit().putString(KEY_PEAK_TEXT, value.name).apply()
            notifyChanged()
        }

    var soundSet: SoundSet
        get() = readEnum(KEY_SOUND_SET, SoundSet.entries.toTypedArray(), SoundSet.DUCK)
        set(value) {
            if (value == soundSet) return
            store.edit().putString(KEY_SOUND_SET, value.name).apply()
            notifyChanged()
        }

    var scale: Float
        get() = store.getFloat(KEY_SCALE, PetGeometry.DEFAULT_SCALE)
            .coerceIn(PetGeometry.MIN_SCALE, PetGeometry.MAX_SCALE)
        set(value) = putFloat(
            KEY_SCALE,
            value.coerceIn(PetGeometry.MIN_SCALE, PetGeometry.MAX_SCALE),
            scale
        )

    var volume: Float
        get() = store.getFloat(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(0f, 1f)
        set(value) = putFloat(KEY_VOLUME, value.coerceIn(0f, 1f), volume)

    var bubbleEnabled: Boolean
        get() = store.getBoolean(KEY_BUBBLE, true)
        set(value) = putBoolean(KEY_BUBBLE, value, bubbleEnabled)

    /** 用自定义文本替代余额与今日已用，文本内容不限，可以带正负号或任意文字。 */
    var customBalanceEnabled: Boolean
        get() = store.getBoolean(KEY_CUSTOM_BALANCE, false)
        set(value) = putBoolean(KEY_CUSTOM_BALANCE, value, customBalanceEnabled)

    var customBalanceText: String
        get() = store.getString(KEY_CUSTOM_BALANCE_TEXT, "").orEmpty()
        set(value) = putString(KEY_CUSTOM_BALANCE_TEXT, value, customBalanceText)

    var customUsageText: String
        get() = store.getString(KEY_CUSTOM_USAGE_TEXT, "").orEmpty()
        set(value) = putString(KEY_CUSTOM_USAGE_TEXT, value, customUsageText)

    /** 只显示随机台词，气泡内不出现任何余额与用量内容。 */
    var hideBalance: Boolean
        get() = store.getBoolean(KEY_HIDE_BALANCE, false)
        set(value) = putBoolean(KEY_HIDE_BALANCE, value, hideBalance)

    var showTodayUsage: Boolean
        get() = store.getBoolean(KEY_TODAY_USAGE, true)
        set(value) = putBoolean(KEY_TODAY_USAGE, value, showTodayUsage)

    var autoStart: Boolean
        get() = store.getBoolean(KEY_AUTO_START, true)
        set(value) = putBoolean(KEY_AUTO_START, value, autoStart)

    var petEnabled: Boolean
        get() = store.getBoolean(KEY_PET_ENABLED, false)
        set(value) = putBoolean(KEY_PET_ENABLED, value, petEnabled)

    var refreshSeconds: Int
        get() = store.getInt(KEY_REFRESH_SECONDS, DEFAULT_REFRESH_SECONDS)
            .coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS)
        set(value) = putInt(
            KEY_REFRESH_SECONDS,
            value.coerceIn(MIN_REFRESH_SECONDS, MAX_REFRESH_SECONDS),
            refreshSeconds
        )

    var bubbleSeconds: Int
        get() = store.getInt(KEY_BUBBLE_SECONDS, DEFAULT_BUBBLE_SECONDS)
            .coerceIn(0, MAX_BUBBLE_SECONDS)
        set(value) = putInt(KEY_BUBBLE_SECONDS, value.coerceIn(0, MAX_BUBBLE_SECONDS), bubbleSeconds)

    var positionFractionX: Float
        get() = store.getFloat(KEY_FRACTION_X, 1f).coerceIn(0f, 1f)
        set(value) = putFloat(KEY_FRACTION_X, value.coerceIn(0f, 1f), positionFractionX)

    var positionFractionY: Float
        get() = store.getFloat(KEY_FRACTION_Y, 1f).coerceIn(0f, 1f)
        set(value) = putFloat(KEY_FRACTION_Y, value.coerceIn(0f, 1f), positionFractionY)

    var snapHorizontal: SnapHorizontal
        get() = readEnum(KEY_SNAP_H, SnapHorizontal.entries.toTypedArray(), SnapHorizontal.NONE)
        set(value) {
            if (value == snapHorizontal) return
            store.edit().putString(KEY_SNAP_H, value.name).apply()
            notifyChanged()
        }

    var snapVertical: SnapVertical
        get() = readEnum(KEY_SNAP_V, SnapVertical.entries.toTypedArray(), SnapVertical.NONE)
        set(value) {
            if (value == snapVertical) return
            store.edit().putString(KEY_SNAP_V, value.name).apply()
            notifyChanged()
        }

    private fun notifyChanged() {
        val snapshot = synchronized(listeners) { listeners.toList() }
        for (listener in snapshot) listener.onPrefsChanged()
    }

    private fun putString(key: String, value: String, current: String) {
        if (value == current) return
        store.edit().putString(key, value).apply()
        notifyChanged()
    }

    private fun putFloat(key: String, value: Float, current: Float) {
        if (value == current) return
        store.edit().putFloat(key, value).apply()
        notifyChanged()
    }

    private fun putInt(key: String, value: Int, current: Int) {
        if (value == current) return
        store.edit().putInt(key, value).apply()
        notifyChanged()
    }

    private fun putBoolean(key: String, value: Boolean, current: Boolean) {
        if (value == current) return
        store.edit().putBoolean(key, value).apply()
        notifyChanged()
    }

    private fun <T : Enum<T>> readEnum(key: String, values: Array<T>, fallback: T): T {
        val raw = store.getString(key, null) ?: return fallback
        return values.firstOrNull { it.name == raw } ?: fallback
    }

    companion object {

        private const val STORE_NAME = "whale_pet_prefs"

        private const val KEY_API_KEY = "api_key"
        private const val KEY_PLATFORM_TOKEN = "platform_token"
        private const val KEY_USAGE_MODE = "usage_mode"
        private const val KEY_PEAK_TEXT = "peak_text"
        private const val KEY_SOUND_SET = "sound_set"
        private const val KEY_SCALE = "scale"
        private const val KEY_VOLUME = "volume"
        private const val KEY_BUBBLE = "bubble_enabled"
        private const val KEY_CUSTOM_BALANCE = "custom_balance_enabled"
        private const val KEY_CUSTOM_BALANCE_TEXT = "custom_balance_text"
        private const val KEY_CUSTOM_USAGE_TEXT = "custom_usage_text"
        private const val KEY_HIDE_BALANCE = "hide_balance"
        private const val KEY_TODAY_USAGE = "show_today_usage"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_PET_ENABLED = "pet_enabled"
        private const val KEY_REFRESH_SECONDS = "refresh_seconds"
        private const val KEY_BUBBLE_SECONDS = "bubble_seconds"
        private const val KEY_FRACTION_X = "position_fraction_x"
        private const val KEY_FRACTION_Y = "position_fraction_y"
        private const val KEY_SNAP_H = "snap_horizontal"
        private const val KEY_SNAP_V = "snap_vertical"

        const val DEFAULT_VOLUME = 0.9f
        const val DEFAULT_REFRESH_SECONDS = 60
        const val MIN_REFRESH_SECONDS = 20
        const val MAX_REFRESH_SECONDS = 600
        const val DEFAULT_BUBBLE_SECONDS = 5
        const val MAX_BUBBLE_SECONDS = 60

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
