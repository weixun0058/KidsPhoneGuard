package com.kidsphoneguard.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用扫描器
 * 获取设备上已安装的应用列表
 */
object AppScanner {

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
            val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

            for (packageInfo in packages) {
                val applicationInfo = packageInfo.applicationInfo
                if (applicationInfo != null) {
                    val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // 如果不包含系统应用，跳过
                    if (!includeSystemApps && isSystemApp) {
                        continue
                    }

                    // 跳过自身应用
                    if (applicationInfo.packageName == context.packageName) {
                        continue
                    }

                    val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                    val icon = packageManager.getApplicationIcon(applicationInfo)

                    apps.add(
                        AppInfo(
                            packageName = applicationInfo.packageName,
                            appName = appName,
                            icon = icon,
                            isSystemApp = isSystemApp
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 按应用名称排序
        apps.sortedBy { it.appName.lowercase() }
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
            null
        }
    }
}
