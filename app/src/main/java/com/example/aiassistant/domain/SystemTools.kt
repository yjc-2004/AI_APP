package com.example.aiassistant.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.aiassistant.services.AgentAccessibilityService
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.app.ActivityManager

/**
 * 存放需要特殊权限的系统级工具
 */
object SystemTools {

    fun simulateClick(x: Int, y: Int): String {
        val success = AgentAccessibilityService.instance?.performGlobalClick(x, y)
        return if (success == true) "坐标($x, $y)点击成功。" else "坐标($x, $y)点击失败，无障碍服务可能未连接。"
    }

    fun inputText(text: String): String {
        val success = AgentAccessibilityService.instance?.inputTextInFocusedField(text)
        return if (success == true) "文本 '$text' 输入成功。" else "输入失败，未找到活动的输入框。"
    }

    fun performBackPress(): String {
        val service = AgentAccessibilityService.instance ?: return "错误: 无障碍服务未连接。"

        // 使用无障碍服务执行全局返回动作
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

        return if (success) "成功执行了返回操作。" else "错误: 执行返回操作失败。"
    }

    /**
     * 根据提供的包名启动一个安卓应用。
     * @param context 上下文环境，用于启动 Activity。
     * @param packageName 要启动的应用的完整包名 (例如 "com.android.settings")。
     * @return 描述操作结果的字符串。
     */
    fun launchApp(context: Context, packageName: String): String = try {

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
            // ★ 关键：清栈 + 新任务
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or     // 清掉旧栈
                        Intent.FLAG_ACTIVITY_CLEAR_TOP         // 可选，进一步保证
            )
            // 再次声明 MAIN / LAUNCHER，兼容部分定制 ROM
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        if (launchIntent != null) {
            context.startActivity(launchIntent)
            "应用 $packageName 已成功启动（已重置至主页面）。"
        } else {
            "错误：未找到包名为 $packageName 的应用。"
        }

    } catch (e: Exception) {
        Log.e("SystemTools", "启动应用失败: $packageName", e)
        "错误：启动应用 $packageName 时发生异常。"
    }


    fun getInstalledApps(context: Context): String {
        return try {
            val pm = context.packageManager
            // 过滤出那些有启动意图的应用，并提取其包名
            val appPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName }

            if (appPackages.isNotEmpty()) {
                // 将列表格式化为换行分隔的字符串
                "已获取到以下可启动的应用包名：\n${appPackages.joinToString("\n")}"
            } else {
                "未找到任何可启动的应用。"
            }
        } catch (e: Exception) {
            Log.e("SystemTools", "获取应用列表失败", e)
            "错误：获取应用列表时发生异常。"
        }
    }

    fun returnToHomeScreen(context: Context): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "已执行返回主屏幕操作。"
    }

}