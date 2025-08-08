package com.example.aiassistant.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.aiassistant.services.AgentAccessibilityService
import android.accessibilityservice.AccessibilityService

/**
 * 存放需要特殊权限的系统级工具
 */
object SystemTools {

    fun simulateClick(x: Int, y: Int): String {
        val success = AgentAccessibilityService.instance?.performGlobalClick(x, y)
        return if (success == true) "坐标($x, $y)点击成功。" else "坐标($x, $y)点击失败，无障碍服务可能未连接。"
    }

    fun openApp(context: Context, packageName: String): String {
        val pm: PackageManager = context.packageManager
        val intent: Intent? = pm.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            context.startActivity(intent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "应用 $packageName 已成功打开。"
        } else {
            "应用 $packageName 未找到，无法打开。"
        }
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
}