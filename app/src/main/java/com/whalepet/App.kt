package com.whalepet

import android.app.Application
import com.whalepet.core.Prefs
import com.whalepet.pet.PetService
import com.whalepet.pet.PetSound

/** 应用入口，建立通知渠道并提前加载音效样本，其余状态由各模块自行初始化。 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        PetService.ensureChannel(this)
        PetSound.get(this).apply(Prefs.get(this))
    }
}
