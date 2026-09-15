package com.whalepet.pet

import com.whalepet.core.Motion
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 单轴弹簧积分器。
 *
 * 数值用半隐式欧拉法按受限步长积分，保证高刚度下依然稳定。
 * 目标随时可以改写，速度从当前状态继续，因此动画可以被中途打断或反向。
 */
class Spring(
    value: Float,
    private val positionEpsilon: Float = 0.5f,
    private val velocityEpsilon: Float = 5f
) {

    var value: Float = value
        private set

    var velocity: Float = 0f
        private set

    var target: Float = value
        private set

    private var stiffness = Motion.stiffnessOf(Motion.FREE_RESPONSE)
    private var dampingRatio = Motion.FREE_DAMPING

    fun snapTo(newValue: Float) {
        value = newValue
        target = newValue
        velocity = 0f
    }

    fun animateTo(
        newTarget: Float,
        responseSeconds: Float,
        damping: Float,
        startVelocity: Float = 0f
    ) {
        target = newTarget
        stiffness = Motion.stiffnessOf(responseSeconds)
        dampingRatio = damping
        velocity = startVelocity
    }

    /** 推进一帧，返回是否仍在运动。 */
    fun advance(deltaSeconds: Float): Boolean {
        if (atRest()) {
            value = target
            velocity = 0f
            return false
        }
        var remaining = deltaSeconds
        while (remaining > 0f) {
            val step = if (remaining > MAX_STEP_SECONDS) MAX_STEP_SECONDS else remaining
            integrate(step)
            remaining -= step
        }
        return true
    }

    private fun integrate(deltaSeconds: Float) {
        val damping = 2f * dampingRatio * sqrt(stiffness)
        val acceleration = -stiffness * (value - target) - damping * velocity
        velocity += acceleration * deltaSeconds
        value += velocity * deltaSeconds
    }

    fun atRest(): Boolean {
        return abs(value - target) < positionEpsilon && abs(velocity) < velocityEpsilon
    }

    companion object {
        private const val MAX_STEP_SECONDS = 1f / 300f
    }
}

/**
 * 三次贝塞尔缓动，参数与 CSS cubic-bezier 一致。
 *
 * 给定进度先解出对应的参数 t，再取曲线上的纵坐标。
 */
class CubicBezier(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float
) {

    fun value(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0f) return 0f
        if (clamped >= 1f) return 1f
        return curve(solveT(clamped))
    }

    private fun solveT(x: Float): Float {
        var low = 0f
        var high = 1f
        var mid = x
        repeat(SOLVER_STEPS) {
            mid = (low + high) / 2f
            if (sampleX(mid) < x) low = mid else high = mid
        }
        return mid
    }

    private fun sampleX(t: Float): Float = coordinate(t, x1, x2)

    private fun curve(t: Float): Float = coordinate(t, y1, y2)

    private fun coordinate(t: Float, first: Float, second: Float): Float {
        val inverse = 1f - t
        return 3f * inverse * inverse * t * first + 3f * inverse * t * t * second + t * t * t
    }

    companion object {
        private const val SOLVER_STEPS = 20
    }
}

/** 缓动曲线，输入输出均为 0 到 1。 */
object Easing {

    /** CSS ease 曲线。 */
    val CSS_EASE: (Float) -> Float = { value -> CubicBezier(0.25f, 0.1f, 0.25f, 1f).value(value) }

    /** CSS cubic-bezier(.34,1.56,.64,1)，用于按压回弹的过冲。 */
    val CSS_BACK_OUT: (Float) -> Float = { value ->
        CubicBezier(0.34f, 1.56f, 0.64f, 1f).value(value)
    }

    val EASE_OUT: (Float) -> Float = { t -> 1f - (1f - t) * (1f - t) }

    val EASE_OUT_CUBIC: (Float) -> Float = { t ->
        val k = 1f - t
        1f - k * k * k
    }
}

/**
 * 定时插值器。
 *
 * 用于透明度、翻转等非弹性过渡，延迟与时长都以秒为单位，可随时从当前值重新指定目标。
 */
class Tween {

    private var from = 0f
    private var to = 0f
    private var durationSeconds = 0.2f
    private var delaySeconds = 0f
    private var curve: (Float) -> Float = Easing.EASE_OUT
    private var elapsed = 0f

    var value = 0f
        private set

    var isRunning = false
        private set

    fun start(
        from: Float,
        to: Float,
        durationSeconds: Float,
        delaySeconds: Float = 0f,
        curve: (Float) -> Float = Easing.EASE_OUT
    ) {
        this.from = from
        this.to = to
        this.durationSeconds = durationSeconds
        this.delaySeconds = delaySeconds
        this.curve = curve
        elapsed = 0f
        value = from
        isRunning = true
    }

    fun jumpTo(newValue: Float) {
        from = newValue
        to = newValue
        elapsed = 0f
        value = newValue
        isRunning = false
    }

    fun advance(deltaSeconds: Float): Boolean {
        if (!isRunning) return false
        elapsed += deltaSeconds
        val progress = if (durationSeconds <= 0f) {
            1f
        } else {
            ((elapsed - delaySeconds) / durationSeconds).coerceIn(0f, 1f)
        }
        value = from + (to - from) * curve(progress)
        if (elapsed >= delaySeconds + durationSeconds) {
            isRunning = false
            return false
        }
        return true
    }
}
