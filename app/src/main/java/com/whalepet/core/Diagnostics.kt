package com.whalepet.core

import android.util.Log

/**
 * 可恢复失败的统一出口。
 *
 * 窗口增删、音频播放、系统页面跳转这类操作失败时会被忽略，忽略本身没问题，
 * 但现象（比如气泡没有出现）需要留下可追查的记录，因此这些地方统一记录一次。
 */
object Diagnostics {

    private const val TAG = "WhalePet"

    fun warn(message: String, error: Throwable) {
        Log.w(TAG, message, error)
    }

    /** 执行一段可能失败的操作，失败时记录消息与异常并返回 null。 */
    inline fun <T> attempt(message: String, block: () -> T): T? {
        return try {
            block()
        } catch (error: Throwable) {
            warn(message, error)
            null
        }
    }
}
