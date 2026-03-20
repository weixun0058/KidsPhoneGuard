package com.kidsphoneguard.utils

/**
 * 白名单管理器
 * 管理系统级白名单应用，这些应用永远不会被锁定
 */
object WhitelistManager {

    /**
     * 系统级白名单（硬编码，不可修改）
     * 包含系统关键应用，确保手机基本功能可用
     */
    val SYSTEM_WHITELIST = setOf(
        // 本应用
        "com.kidsphoneguard",

        // 系统界面类
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher",
        "com.samsung.android.launcher",
        "com.coloros.launcher",
        "com.funtouch.launcher",
        "com.oppo.launcher",
        "com.realme.launcher",
        "com.oneplus.launcher",

        // MIUI系统组件（负一屏、搜索等过渡界面）
        "com.miui.personalassistant",
        "com.android.quicksearchbox",
        "com.miui.quicksearchbox",
        "com.miui.voiceassist",
        "com.miui.yellowpage",
        "com.miui.notification",
        "com.miui.system",
        "com.miui.core",
        "com.miui.framework",

        // 其他系统过渡界面
        "com.android.recents",
        "com.android.switch",
        "com.android.tasks",
        "com.android.wallpaper",
        "com.android.wallpaperpicker",

        // 输入法类
        "com.android.inputmethod",
        "com.google.android.inputmethod",
        "com.miui.securityinputmethod",
        "com.sohu.inputmethod.sogou.xiaomi",
        "com.baidu.input",
        "com.sohu.inputmethod",
        "com.tencent.qqpinyin",
        "com.iflytek.inputmethod",
        "com.touchtype.swiftkey",

        // 系统管理类
        "com.huawei.systemmanager",
        "com.hihonor.systemmanager",
        "com.miui.securitycenter",
        "com.coloros.securitycenter",
        "com.samsung.android.lool",

        // 通讯类（关键）
        "com.android.dialer",
        "com.android.contacts",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.huawei.contacts",
        "com.huawei.phone",
        "com.huawei.message",
        "com.miui.contacts",
        "com.miui.phone",
        "com.miui.message",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
        "com.android.incallui",
        "com.android.server.telecom",

        // 系统设置类
        "com.android.settings",
        "com.miui.settings",
        "com.huawei.settings",
        "com.samsung.android.settings",

        // 紧急呼叫相关
        "com.android.emergency",
        "com.google.android.apps.emergency",

        // 相机（紧急情况下可能需要）
        "com.android.camera",
        "com.huawei.camera",
        "com.miui.camera",
        "com.samsung.android.camera",

        // 图库（查看照片）
        "com.android.gallery",
        "com.miui.gallery",
        "com.huawei.gallery",
        "com.samsung.android.gallery3d",

        // 文件管理器
        "com.android.filemanager",
        "com.miui.filemanager",
        "com.huawei.filemanager",
        "com.samsung.android.myfiles",

        // 时钟/闹钟
        "com.android.deskclock",
        "com.miui.deskclock",
        "com.huawei.deskclock",
        "com.samsung.android.deskclock",

        // 日历
        "com.android.calendar",
        "com.miui.calendar",
        "com.huawei.calendar",
        "com.samsung.android.calendar",

        // 计算器
        "com.android.calculator2",
        "com.miui.calculator",
        "com.huawei.calculator",
        "com.samsung.android.calculator",

        // 录音机
        "com.android.soundrecorder",
        "com.miui.soundrecorder",
        "com.huawei.soundrecorder",
        "com.samsung.android.voicerecorder",

        // 浏览器（系统自带）
        "com.android.browser",
        "com.miui.browser",
        "com.huawei.browser",
        "com.samsung.android.browser",

        // 应用商店（系统自带）
        "com.android.vending",
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.samsung.android.galaxyapps",
        "com.heytap.market",

        // 钱包/支付（可能需要）
        "com.android.wallet",
        "com.huawei.wallet",
        "com.miui.wallet",
        "com.samsung.android.spay",

        // 健康相关
        "com.google.android.apps.fitness",
        "com.huawei.health",
        "com.miui.health",
        "com.samsung.android.shealth",

        // 地图/导航（紧急情况下可能需要）
        "com.google.android.apps.maps",
        "com.baidu.BaiduMap",
        "com.autonavi.minimap",
        "com.tencent.map",
        "com.huawei.maps",

        // 天气
        "com.android.weather",
        "com.miui.weather",
        "com.huawei.weather",
        "com.samsung.android.weather",

        // 邮件
        "com.android.email",
        "com.google.android.gm",
        "com.huawei.email",
        "com.samsung.android.email",

        // 备忘录/笔记
        "com.android.notes",
        "com.miui.notes",
        "com.huawei.notepad",
        "com.samsung.android.app.notes",

        // 音乐播放器
        "com.android.music",
        "com.miui.player",
        "com.huawei.music",
        "com.samsung.android.music",

        // 视频播放器
        "com.android.video",
        "com.miui.videoplayer",
        "com.huawei.video",
        "com.samsung.android.video",

        // 下载管理器
        "com.android.providers.downloads",
        "com.android.downloadmanager",

        // 打印服务
        "com.android.printspooler",

        // VPN服务
        "com.android.vpndialogs",

        // 系统更新
        "com.android.updater",
        "com.miui.updater",
        "com.huawei.android.hwupgrade",
        "com.samsung.android.softwareupdate",

        // 备份与恢复
        "com.android.backupconfirm",
        "com.huawei.KoBackup",
        "com.miui.backup",
        "com.samsung.android.scloud",

        // 查找我的设备
        "com.google.android.gms.location.history",
        "com.huawei.android.findmyphone",
        "com.samsung.android.fmm",

        // 语音助手
        "com.google.android.googlequicksearchbox",
        "com.huawei.vassistant",
        "com.miui.voiceassist",
        "com.samsung.android.bixby.agent",

        // 智能设备/家居
        "com.google.android.apps.home",
        "com.huawei.smarthome",
        "com.miui.smarthome",
        "com.samsung.android.oneconnect"
    )

    /**
     * 通讯类应用（可单独查询）
     */
    val COMMUNICATION_APPS = setOf(
        "com.android.dialer",
        "com.android.contacts",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.huawei.contacts",
        "com.huawei.phone",
        "com.huawei.message",
        "com.miui.contacts",
        "com.miui.phone",
        "com.miui.message",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
        "com.android.incallui",
        "com.android.server.telecom"
    )

    /**
     * 检查包名是否在白名单中
     * @param packageName 应用包名
     * @return 如果在白名单中返回true，否则返回false
     */
    fun isInWhitelist(packageName: String): Boolean {
        return SYSTEM_WHITELIST.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * 检查是否是通讯类应用
     * @param packageName 应用包名
     * @return 如果是通讯应用返回true
     */
    fun isCommunicationApp(packageName: String): Boolean {
        return COMMUNICATION_APPS.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * 检查是否是本应用
     * @param packageName 应用包名
     * @return 如果是本应用返回true
     */
    fun isSelfApp(packageName: String): Boolean {
        return packageName.contains("com.kidsphoneguard", ignoreCase = true)
    }

    /**
     * 检查是否是系统桌面/启动器
     * @param packageName 应用包名
     * @return 如果是桌面应用返回true
     */
    fun isLauncher(packageName: String): Boolean {
        val launchers = setOf(
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            "com.samsung.android.launcher",
            "com.coloros.launcher",
            "com.funtouch.launcher",
            "com.oppo.launcher",
            "com.realme.launcher",
            "com.oneplus.launcher"
        )
        return launchers.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * 检查是否是系统设置
     * @param packageName 应用包名
     * @return 如果是设置应用返回true
     */
    fun isSettings(packageName: String): Boolean {
        return packageName.contains("com.android.settings", ignoreCase = true) ||
               packageName.contains("com.miui.settings", ignoreCase = true) ||
               packageName.contains("com.huawei.settings", ignoreCase = true) ||
               packageName.contains("com.samsung.android.settings", ignoreCase = true)
    }

    /**
     * 检查是否是系统电话应用
     * @param packageName 应用包名
     * @return 如果是电话应用返回true
     */
    fun isPhoneApp(packageName: String): Boolean {
        val phoneApps = setOf(
            "com.android.dialer",
            "com.android.phone",
            "com.google.android.dialer",
            "com.huawei.phone",
            "com.miui.phone",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.android.server.telecom"
        )
        return phoneApps.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * 检查是否是系统短信应用
     * @param packageName 应用包名
     * @return 如果是短信应用返回true
     */
    fun isMessagingApp(packageName: String): Boolean {
        val messagingApps = setOf(
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.huawei.message",
            "com.miui.message",
            "com.samsung.android.messaging"
        )
        return messagingApps.any { packageName.contains(it, ignoreCase = true) }
    }
}
