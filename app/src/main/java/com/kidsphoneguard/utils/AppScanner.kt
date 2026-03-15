package com.kidsphoneguard.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用扫描器
 * 获取设备上已安装的应用列表
 * 适配Android 11+的包可见性限制及各厂商定制系统
 */
object AppScanner {

    private const val TAG = "AppScanner"

    /**
     * 应用信息数据类
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val isSystemApp: Boolean
    )

    /**
     * 获取已安装的应用列表
     * 适配Android 11+ (API 30) 的包可见性限制
     * 适配MIUI/HarmonyOS等厂商定制系统
     *
     * @param context 上下文
     * @param includeSystemApps 是否包含系统应用
     * @return 应用列表
     */
    suspend fun getInstalledApps(
        context: Context,
        includeSystemApps: Boolean = false
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val apps = mutableListOf<AppInfo>()

        try {
            // 方法1: 使用queryIntentActivities获取可启动应用（推荐，兼容性最好）
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)

            Log.d(TAG, "通过queryIntentActivities获取到 ${resolveInfos.size} 个应用")

            for (resolveInfo in resolveInfos) {
                try {
                    val activityInfo = resolveInfo.activityInfo
                    val applicationInfo = activityInfo.applicationInfo
                    val packageName = activityInfo.packageName

                    // 跳过自身应用
                    if (packageName == context.packageName) {
                        continue
                    }

                    val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // 如果不包含系统应用，跳过
                    if (!includeSystemApps && isSystemApp) {
                        continue
                    }

                    val appName = resolveInfo.loadLabel(packageManager).toString()
                    val icon = resolveInfo.loadIcon(packageManager)

                    // 避免重复添加
                    if (apps.none { it.packageName == packageName }) {
                        apps.add(
                            AppInfo(
                                packageName = packageName,
                                appName = appName,
                                icon = icon,
                                isSystemApp = isSystemApp
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "处理应用信息时出错: ${e.message}")
                }
            }

            // 方法2: 如果方法1获取的应用太少，尝试使用getInstalledPackages作为备选
            if (apps.size < 5) {
                Log.d(TAG, "应用数量过少，尝试备选方案")
                apps.addAll(getAppsFromInstalledPackages(context, packageManager, includeSystemApps))
            }

        } catch (e: Exception) {
            Log.e(TAG, "获取应用列表失败: ${e.message}", e)
            // 出错时尝试备选方案
            try {
                apps.addAll(getAppsFromInstalledPackages(context, packageManager, includeSystemApps))
            } catch (e2: Exception) {
                Log.e(TAG, "备选方案也失败: ${e2.message}", e2)
            }
        }

        // 去重并排序
        val uniqueApps = apps.distinctBy { it.packageName }
        val sortedApps = uniqueApps.sortedBy { it.appName.lowercase() }

        Log.d(TAG, "最终返回 ${sortedApps.size} 个应用")
        sortedApps
    }

    /**
     * 备选方案：使用getInstalledPackages获取应用列表
     * 适用于queryIntentActivities受限的情况
     */
    private fun getAppsFromInstalledPackages(
        context: Context,
        packageManager: PackageManager,
        includeSystemApps: Boolean
    ): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()

        try {
            // Android 11+ 需要QUERY_ALL_PACKAGES权限才能获取完整列表
            // 使用API 33的常量值(33)而不是Build.VERSION_CODES.TIRAMISU，避免低版本闪退
            @Suppress("DEPRECATION")
            val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

            Log.d(TAG, "通过getInstalledPackages获取到 ${packages.size} 个包")

            for (packageInfo in packages) {
                try {
                    val applicationInfo = packageInfo.applicationInfo
                    if (applicationInfo == null) continue

                    val packageName = applicationInfo.packageName

                    // 跳过自身应用
                    if (packageName == context.packageName) {
                        continue
                    }

                    val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // 如果不包含系统应用，跳过
                    if (!includeSystemApps && isSystemApp) {
                        continue
                    }

                    // 过滤掉没有界面的应用（通过检查是否有启动Activity）
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent == null && !includeSystemApps) {
                        continue
                    }

                    val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                    val icon = packageManager.getApplicationIcon(applicationInfo)

                    apps.add(
                        AppInfo(
                            packageName = packageName,
                            appName = appName,
                            icon = icon,
                            isSystemApp = isSystemApp
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "处理包信息时出错: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getInstalledPackages失败: ${e.message}", e)
        }

        return apps
    }

    /**
     * 搜索应用
     * @param context 上下文
     * @param query 搜索关键词
     * @param includeSystemApps 是否包含系统应用
     * @return 匹配的应用列表
     */
    suspend fun searchApps(
        context: Context,
        query: String,
        includeSystemApps: Boolean = false
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        val allApps = getInstalledApps(context, includeSystemApps)
        if (query.isBlank()) {
            return@withContext allApps
        }

        val lowerQuery = query.lowercase()
        allApps.filter { app ->
            app.appName.lowercase().contains(lowerQuery) ||
            app.packageName.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 获取应用信息
     * @param context 上下文
     * @param packageName 包名
     * @return 应用信息，如果未找到返回null
     */
    fun getAppInfo(context: Context, packageName: String): AppInfo? {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
            val icon = packageManager.getApplicationIcon(applicationInfo)

            AppInfo(
                packageName = packageName,
                appName = appName,
                icon = icon,
                isSystemApp = isSystemApp
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "应用未找到: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取应用信息失败: $packageName, ${e.message}")
            null
        }
    }

    /**
     * 检查是否可以获取应用列表
     * 用于诊断权限问题
     */
    fun canGetAppList(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
            Log.d(TAG, "权限检查: 可以获取 ${resolveInfos.size} 个应用")
            resolveInfos.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "权限检查失败: ${e.message}")
            false
        }
    }

    /**
     * 获取权限状态信息
     * 用于诊断和日志记录
     */
    fun getPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "QUERY_ALL_PACKAGES" to hasQueryAllPackagesPermission(context),
            "GET_INSTALLED_APPS" to canGetAppList(context),
            "PACKAGE_USAGE_STATS" to PermissionManager.hasUsageStatsPermission(context)
        )
    }

    /**
     * 检查是否有QUERY_ALL_PACKAGES权限
     */
    private fun hasQueryAllPackagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.checkSelfPermission(android.Manifest.permission.QUERY_ALL_PACKAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 11以下不需要此权限
        }
    }
}
