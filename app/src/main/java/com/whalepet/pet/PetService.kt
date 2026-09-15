package com.whalepet.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.whalepet.R
import com.whalepet.core.BubbleContent
import com.whalepet.core.Diagnostics
import com.whalepet.core.PetState
import com.whalepet.core.Prefs
import com.whalepet.ui.HomeActivity
import kotlinx.coroutines.launch

/**
 * 桌宠前台服务。
 *
 * 承载悬浮窗、音效与刷新节奏。通知栏常驻一条低优先级条目，用于展示余额并提供刷新与收起操作。
 */
class PetService : LifecycleService() {

    private lateinit var prefs: Prefs
    private lateinit var state: PetState
    private lateinit var sound: PetSound
    private lateinit var window: PetWindow
    private val content = BubbleContent(this)

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var receiver: BroadcastReceiver? = null

    /** 刷新间隔与音效设置变化后立即生效。 */
    private val prefsListener = Prefs.Listener {
        sound.apply(prefs)
        scheduleRefresh()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        state = PetState.get(this)
        sound = PetSound.get(this)
        window = PetWindow(this, prefs, state, sound)
        window.attach()
        registerActions()
        lifecycleScope.launch {
            state.snapshot.collect { snapshot ->
                window.onSnapshot(snapshot)
                updateNotification(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state.snapshot.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        window.refreshMetrics()
        scheduleRefresh()
        state.refresh(manual = false)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.refreshMetrics()
    }

    override fun onDestroy() {
        prefs.removeListener(prefsListener)
        refreshRunnable?.let { handler.removeCallbacks(it) }
        receiver?.let { active ->
            Diagnostics.attempt("注销广播接收器失败") { unregisterReceiver(active) }
        }
        window.detach()
        sound.stop()
        super.onDestroy()
    }

    private fun scheduleRefresh() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            state.refresh(manual = false)
            scheduleRefresh()
        }
        refreshRunnable = runnable
        handler.postDelayed(runnable, prefs.refreshSeconds * 1000L)
    }

    private fun registerActions() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(ACTION_REFRESH)
            addAction(ACTION_STOP)
        }
        val target = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_REFRESH -> {
                        state.refresh(manual = true)
                    }
                    ACTION_STOP -> {
                        prefs.petEnabled = false
                        stopSelf()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(this, target, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = target
    }

    private fun updateNotification(snapshot: PetState.Snapshot) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: PetState.Snapshot): Notification {
        val display = content.display(snapshot, content.settingsOf(prefs))
        val text = when {
            snapshot.status == PetState.Status.ERROR ->
                snapshot.message ?: getString(R.string.status_failed)
            display.usage != null ->
                getString(R.string.notification_text, display.balance, display.usage)
            else -> display.balance
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(settingsIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        builder.addAction(0, getString(R.string.action_refresh), actionIntent(ACTION_REFRESH))
        builder.addAction(0, getString(R.string.action_hide), actionIntent(ACTION_STOP))
        return builder.build()
    }

    private fun settingsIntent(): PendingIntent {
        val intent = Intent(this, HomeActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {

        const val CHANNEL_ID = "whale_pet_status"

        private const val NOTIFICATION_ID = 1001
        private const val ACTION_REFRESH = "com.whalepet.action.REFRESH"
        private const val ACTION_STOP = "com.whalepet.action.STOP"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = context.getString(R.string.notification_channel_description)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, PetService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetService::class.java))
        }
    }
}
