package com.kidsphoneguard.utils

/**
 * 系统受保护表面分类器。
 *
 * 这里只回答“当前包名是否属于应重点防护的系统设置、安装器或应用市场表面”，
 * 不承担白名单放行职责。设置类前缀匹配是拦截候选的扩大识别，不能改成精确匹配。
 */
object SystemSurfaceClassifier {

    private val settingsPackages = setOf(
        "com.android.settings",
        "com.miui.settings",
        "com.miui.securitycenter",
        "com.xiaomi.misettings",
        "com.lbe.security.miui",
        "com.miui.powerkeeper",
        "com.huawei.settings",
        "com.huawei.systemmanager",
        "com.huawei.security.privacycenter",
        "com.huawei.ohos.security.privacycenter",
        "com.huawei.securitymgr",
        "com.huawei.devicemanager",
        "com.huawei.controlcenter",
        "com.hihonor.settings",
        "com.hihonor.systemmanager",
        "com.samsung.android.settings"
    )

    private val installerPackages = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    private val appMarketPackages = setOf(
        "com.android.vending",
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.samsung.android.galaxyapps",
        "com.heytap.market"
    )

    fun isSettingsSurface(packageName: String): Boolean =
        matchesPackageOrSubpackage(packageName, settingsPackages)

    fun isInstallerOrMarketSurface(packageName: String): Boolean =
        isPackageInstallerSurface(packageName) || isAppMarketSurface(packageName)

    fun isPackageInstallerSurface(packageName: String): Boolean =
        matchesPackageOrSubpackage(packageName, installerPackages)

    fun isAppMarketSurface(packageName: String): Boolean =
        matchesPackageOrSubpackage(packageName, appMarketPackages)

    private fun matchesPackageOrSubpackage(packageName: String, packageFamilies: Set<String>): Boolean {
        val normalized = packageName.trim().substringBefore(':').lowercase()
        return packageFamilies.any { family ->
            normalized == family || normalized.startsWith("$family.")
        }
    }
}
