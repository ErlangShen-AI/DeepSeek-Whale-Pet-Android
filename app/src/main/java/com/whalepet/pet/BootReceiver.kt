package com.whalepet.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whalepet.core.Diagnostics
import com.whalepet.core.Prefs

/** 开机与覆盖安装后按用户设置恢复桌宠，未开启悬浮窗权限时不做任何处理。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Prefs.get(context)
        if (!prefs.autoStart || !prefs.petEnabled) return
        Diagnostics.attempt("开机恢复桌宠失败") { PetService.start(context) }
    }
}
