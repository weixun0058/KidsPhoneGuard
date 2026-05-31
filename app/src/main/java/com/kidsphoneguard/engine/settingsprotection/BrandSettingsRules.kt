package com.kidsphoneguard.engine.settingsprotection

import android.os.Build

interface BrandSettingsRules {
    val protectedSettingPackages: Set<String>
    val targetAppKeywords: Set<String>
    val riskyCapabilityKeywords: Set<String>
    val riskyActionKeywords: Set<String>
    val observeOnlyKeywords: Set<String>
    val guardianDisruptiveCapabilityKeywords: Set<String>
        get() = emptySet()
    val guardianDisruptiveActionKeywords: Set<String>
        get() = emptySet()
}

object GenericAndroidSettingsRules : BrandSettingsRules {
    override val protectedSettingPackages = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.vending",
        "com.samsung.android.settings",
        "com.samsung.android.galaxyapps",
        "com.android.systemui"
    )

    override val targetAppKeywords = setOf(
        "拉钩守护",
        "儿童手机守护",
        "KidsPhoneGuard",
        "com.kidsphoneguard"
    )

    override val riskyCapabilityKeywords = setOf(
        "无障碍",
        "辅助功能",
        "悬浮窗",
        "显示在其他应用上层",
        "使用情况访问",
        "使用记录访问",
        "电池优化",
        "忽略电池优化",
        "后台运行",
        "自启动",
        "关联启动",
        "设备管理器",
        "设备管理应用",
        "通知",
        "权限管理",
        "accessibility",
        "display over other apps",
        "usage access",
        "battery optimization",
        "device admin",
        "notifications",
        "permissions"
    )

    override val riskyActionKeywords = setOf(
        "关闭",
        "关闭服务",
        "停用",
        "停止",
        "不允许",
        "拒绝",
        "确定",
        "撤销",
        "移除",
        "强行停止",
        "清除数据",
        "turn off",
        "disable",
        "deny",
        "revoke",
        "remove",
        "force stop",
        "clear data"
    )

    override val observeOnlyKeywords = setOf(
        "设置",
        "应用信息",
        "应用管理",
        "权限",
        "通知",
        "settings",
        "app info",
        "permissions"
    )

    override val guardianDisruptiveCapabilityKeywords = setOf(
        "超级省电",
        "超级省电模式",
        "极限省电",
        "极限省电模式",
        "超长待机",
        "超级待机",
        "ultra power saving",
        "ultra power mode",
        "super power saving",
        "extreme power saving"
    )

    override val guardianDisruptiveActionKeywords = setOf(
        "开启",
        "启用",
        "进入",
        "打开",
        "确定",
        "立即开启",
        "立即启用",
        "turn on",
        "enable",
        "enter",
        "ok",
        "confirm"
    )
}

object XiaomiSettingsRules : BrandSettingsRules {
    override val protectedSettingPackages =
        GenericAndroidSettingsRules.protectedSettingPackages + setOf(
            "com.miui.settings",
            "com.miui.securitycenter",
            "com.xiaomi.misettings",
            "com.miui.powerkeeper",
            "com.lbe.security.miui",
            "com.miui.packageinstaller",
            "com.xiaomi.market"
        )
    override val targetAppKeywords = GenericAndroidSettingsRules.targetAppKeywords
    override val riskyCapabilityKeywords =
        GenericAndroidSettingsRules.riskyCapabilityKeywords + setOf(
            "手机管家",
            "应用启动管理",
            "省电策略",
            "无限制",
            "后台活动",
            "后台弹出界面",
            "开机启动"
        )
    override val riskyActionKeywords = GenericAndroidSettingsRules.riskyActionKeywords
    override val observeOnlyKeywords =
        GenericAndroidSettingsRules.observeOnlyKeywords + setOf("安全中心", "手机管家", "应用管理")
}

object HuaweiSettingsRules : BrandSettingsRules {
    override val protectedSettingPackages =
        GenericAndroidSettingsRules.protectedSettingPackages + setOf(
            "com.huawei.settings",
            "com.huawei.systemmanager",
            "com.huawei.security.privacycenter",
            "com.huawei.ohos.security.privacycenter",
            "com.huawei.securitymgr",
            "com.huawei.devicemanager",
            "com.huawei.controlcenter",
            "com.huawei.appmarket"
        )
    override val targetAppKeywords = GenericAndroidSettingsRules.targetAppKeywords
    override val riskyCapabilityKeywords =
        GenericAndroidSettingsRules.riskyCapabilityKeywords + setOf(
            "应用启动管理",
            "允许自启动",
            "允许关联启动",
            "允许后台活动",
            "电池优化"
        )
    override val riskyActionKeywords = GenericAndroidSettingsRules.riskyActionKeywords
    override val observeOnlyKeywords =
        GenericAndroidSettingsRules.observeOnlyKeywords + setOf("手机管家", "应用启动管理")
}

object HonorSettingsRules : BrandSettingsRules {
    override val protectedSettingPackages =
        GenericAndroidSettingsRules.protectedSettingPackages + setOf(
            "com.hihonor.settings",
            "com.hihonor.systemmanager",
            "com.huawei.security.privacycenter",
            "com.huawei.ohos.security.privacycenter",
            "com.huawei.securitymgr",
            "com.huawei.devicemanager",
            "com.huawei.controlcenter",
            "com.hihonor.appmarket"
        )
    override val targetAppKeywords = GenericAndroidSettingsRules.targetAppKeywords
    override val riskyCapabilityKeywords =
        GenericAndroidSettingsRules.riskyCapabilityKeywords + setOf(
            "应用启动管理",
            "允许自启动",
            "允许关联启动",
            "允许后台活动",
            "电池优化"
        )
    override val riskyActionKeywords = GenericAndroidSettingsRules.riskyActionKeywords
    override val observeOnlyKeywords =
        GenericAndroidSettingsRules.observeOnlyKeywords + setOf("手机管家", "应用启动管理")
}

object OppoSettingsRules : BrandSettingsRules {
    override val protectedSettingPackages = GenericAndroidSettingsRules.protectedSettingPackages
    override val targetAppKeywords = GenericAndroidSettingsRules.targetAppKeywords
    override val riskyCapabilityKeywords = GenericAndroidSettingsRules.riskyCapabilityKeywords
    override val riskyActionKeywords = GenericAndroidSettingsRules.riskyActionKeywords
    override val observeOnlyKeywords = GenericAndroidSettingsRules.observeOnlyKeywords
}

object VivoSettingsRules : BrandSettingsRules {
    override val protectedSettingPackages = GenericAndroidSettingsRules.protectedSettingPackages
    override val targetAppKeywords = GenericAndroidSettingsRules.targetAppKeywords
    override val riskyCapabilityKeywords = GenericAndroidSettingsRules.riskyCapabilityKeywords
    override val riskyActionKeywords = GenericAndroidSettingsRules.riskyActionKeywords
    override val observeOnlyKeywords = GenericAndroidSettingsRules.observeOnlyKeywords
}

object BrandSettingsRuleProvider {
    fun currentRules(): List<BrandSettingsRules> {
        val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
        val brand = Build.BRAND?.lowercase().orEmpty()
        val brandRules = when {
            manufacturer.contains("xiaomi") ||
                brand.contains("xiaomi") ||
                brand.contains("redmi") ||
                brand.contains("poco") -> XiaomiSettingsRules
            manufacturer.contains("huawei") || brand.contains("huawei") -> HuaweiSettingsRules
            manufacturer.contains("honor") || brand.contains("honor") -> HonorSettingsRules
            manufacturer.contains("oppo") ||
                brand.contains("oppo") ||
                brand.contains("oneplus") ||
                brand.contains("realme") -> OppoSettingsRules
            manufacturer.contains("vivo") || brand.contains("vivo") -> VivoSettingsRules
            else -> GenericAndroidSettingsRules
        }

        return if (brandRules === GenericAndroidSettingsRules) {
            listOf(GenericAndroidSettingsRules)
        } else {
            listOf(GenericAndroidSettingsRules, brandRules)
        }
    }
}
