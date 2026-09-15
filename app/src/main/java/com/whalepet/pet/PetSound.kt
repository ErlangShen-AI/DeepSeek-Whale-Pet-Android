package com.whalepet.pet

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.whalepet.R
import com.whalepet.core.Diagnostics
import com.whalepet.core.Prefs
import com.whalepet.core.SoundSet

/**
 * 按压音效。
 *
 * 按压是一段状态而不是一次事件：按住时播放按压样本，松手时把松手样本排进按压样本的尾音，
 * 同一状态重复设置不产生新的播放，因此重复送达的触摸事件不会叠加多路声音。
 * 每个样本只保留一路流，播放前先停掉全部正在播放的流。
 */
class PetSound private constructor(context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val pool: SoundPool
    private val sampleIds = HashMap<Int, Int>()
    private val streamIds = HashMap<Int, Int>()
    private val durations = HashMap<Int, Long>()
    private val loaded = mutableSetOf<Int>()

    private var soundSet = SoundSet.DUCK
    private var volume = Prefs.DEFAULT_VOLUME
    private var pressed = false
    private var pressStartedAt = 0L
    private var pressDurationMs = 0L

    private val releaseRunnable = Runnable { playRelease() }

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            loaded.add(sampleId)
            warmUp()
        }
        for (resource in SAMPLE_RESOURCES) {
            sampleIds[resource] = pool.load(context, resource, 1)
            durations[resource] = measureDuration(context, resource)
        }
    }

    private var warmed = false

    /** 样本全部就位后静音播一遍按压样本，音频通道提前建好，第一次按下不会迟到或被截短。 */
    private fun warmUp() {
        if (warmed || loaded.size < sampleIds.size) return
        warmed = true
        for (resource in WARMUP_RESOURCES) {
            val sampleId = sampleIds[resource] ?: continue
            Diagnostics.attempt("预热音频失败") { pool.play(sampleId, 0f, 0f, PRIORITY, 0, 1f) }
        }
    }

    /** 音效集合或音量变化后立即生效，关闭音效时停掉正在播放的流。 */
    fun apply(prefs: Prefs) {
        soundSet = prefs.soundSet
        volume = prefs.volume
        if (!isEnabled()) stopAll()
    }

    /** 手势状态入口，按下与松手都经过这里，重复状态被忽略。 */
    fun setPressed(pressed: Boolean) {
        if (this.pressed == pressed) return
        this.pressed = pressed
        if (pressed) startPress() else scheduleRelease()
    }

    /** 试听：完整走一遍按下与松手，用于确认当前音效集合与音量。 */
    fun preview() {
        setPressed(true)
        handler.postDelayed({ setPressed(false) }, PREVIEW_HOLD_MS)
    }

    /** 清掉按压状态与正在播放的流，音效池保留给进程内其他调用方复用。 */
    fun stop() {
        handler.removeCallbacks(releaseRunnable)
        pressed = false
        stopAll()
    }

    private fun startPress() {
        if (!isEnabled()) return
        handler.removeCallbacks(releaseRunnable)
        stop(releaseResource())
        val resource = pressResource()
        val duration = durations[resource] ?: 0L
        pressDurationMs = if (duration >= MIN_VALID_DURATION_MS) duration else 0L
        pressStartedAt = SystemClock.uptimeMillis()
        play(resource)
    }

    /** 松手样本排在按压样本结束前 100 毫秒，两者尾音相接；按压样本已经放完时立即播放。 */
    private fun scheduleRelease() {
        if (!isEnabled()) return
        handler.removeCallbacks(releaseRunnable)
        val remaining = if (pressDurationMs <= 0L) {
            FALLBACK_RELEASE_DELAY_MS
        } else {
            val elapsed = SystemClock.uptimeMillis() - pressStartedAt
            (pressDurationMs - elapsed - RELEASE_OVERLAP_MS).coerceAtLeast(MIN_RELEASE_DELAY_MS)
        }
        handler.postDelayed(releaseRunnable, remaining)
    }

    private fun isEnabled(): Boolean = soundSet != SoundSet.OFF && volume > 0f

    private fun pressResource(): Int =
        if (soundSet == SoundSet.FX1) R.raw.fx_press else R.raw.duck_press

    private fun releaseResource(): Int =
        if (soundSet == SoundSet.FX1) R.raw.fx_release else R.raw.duck_release

    private fun play(resource: Int) {
        val sampleId = sampleIds[resource] ?: return
        if (!loaded.contains(sampleId)) return
        stop(resource)
        val stream = Diagnostics.attempt("播放音频失败") { pool.play(sampleId, volume, volume, PRIORITY, 0, 1f) }
            ?: 0
        if (stream != 0) streamIds[resource] = stream
    }

    private fun playRelease() {
        play(releaseResource())
    }

    private fun stop(resource: Int) {
        val stream = streamIds.remove(resource) ?: return
        Diagnostics.attempt("停止音频失败") { pool.stop(stream) }
    }

    private fun stopAll() {
        if (streamIds.isEmpty()) return
        for (stream in streamIds.values) {
            Diagnostics.attempt("停止音频失败") { pool.stop(stream) }
        }
        streamIds.clear()
    }

    private fun measureDuration(context: Context, resource: Int): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse("android.resource://${context.packageName}/$resource")
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (error: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val MAX_STREAMS = 6
        private const val PRIORITY = 1
        private const val RELEASE_OVERLAP_MS = 100L
        private const val MIN_RELEASE_DELAY_MS = 60L
        private const val FALLBACK_RELEASE_DELAY_MS = 400L
        private const val PREVIEW_HOLD_MS = 260L
        private const val MIN_VALID_DURATION_MS = 100L

        private val WARMUP_RESOURCES = intArrayOf(R.raw.duck_press, R.raw.fx_press)

        private val SAMPLE_RESOURCES = intArrayOf(
            R.raw.duck_press,
            R.raw.duck_release,
            R.raw.fx_press,
            R.raw.fx_release
        )

        @Volatile
        private var instance: PetSound? = null

        /** 进程内共享同一个音效池，样本在应用启动时就开始加载。 */
        fun get(context: Context): PetSound {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: PetSound(context.applicationContext).also { instance = it }
            }
        }
    }
}
