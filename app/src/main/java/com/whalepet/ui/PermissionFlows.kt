package com.whalepet.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 系统权限的判断与系统页面入口。
 *
 * 负责「这项授权是否生效」的判定、给出对应的系统页面意图，以及何时需要请求通知权限；
 * 跳转由调用方用自己注册的启动器完成，因此这里不持有 Activity。
 */
class PermissionFlows(private val context: Context) {

    fun overlayGranted(): Boolean = Settings.canDrawOverlays(context)

    fun notificationsGranted(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ignoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 悬浮窗权限页。 */
    fun overlayIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    /** 电池优化页：已豁免时进列表页，未豁免时直接申请本应用。 */
    fun batteryIntent(): Intent = if (ignoringBatteryOptimizations()) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    }

    /** 本应用的通知设置页。 */
    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** 应用详情页，系统页面不可用时的兜底入口。 */
    fun appDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    /** 需要请求通知权限时交给调用方触发，Android 13 以下不需要运行时授权。 */
    fun requestNotificationPermission(launch: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
