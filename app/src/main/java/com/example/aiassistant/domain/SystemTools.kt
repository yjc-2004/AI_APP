package com.example.aiassistant.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.aiassistant.services.AgentAccessibilityService
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.app.ActivityManager
import android.os.SystemClock
import android.view.MotionEvent  // 同时确保MotionEvent的导入
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import android.app.Instrumentation
import android.graphics.Point
import android.view.Display
import android.view.WindowManager

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

    /**
     * 滑动屏幕（优化版：增加屏幕边界自适应和完整异常处理）
     * 支持上下左右四个方向，根据屏幕尺寸自动调整滑动距离
     * @param direction 滑动方向，可选值："up"（上滑）、"down"（下滑）、"left"（左滑）、"right"（右滑）
     * @param distance 滑动像素距离（正数）
     * @param duration 执行时间（毫秒）
     * @return 操作结果描述
     */
    fun scrollScreen(direction: String, distance: Int, duration: Int): String {
        // 参数校验
        if (distance <= 0) {
            return "错误：滑动距离必须为正数"
        }
        if (duration <= 0) {
            return "错误：执行时间必须为正数"
        }
        val validDirections = listOf("up", "down", "left", "right")
        if (direction !in validDirections) {
            return "错误：方向必须是以下值之一：${validDirections.joinToString()}"
        }

        val service = AgentAccessibilityService.instance ?: return "错误: 无障碍服务未连接。"

        try {
            // 获取屏幕尺寸
            val context = service.applicationContext
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return "错误：无法获取窗口管理器"

            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            val screenWidth = size.x
            val screenHeight = size.y

            // 自适应边界：根据方向限制最大滑动距离
            val actualDistance = when (direction) {
                "up", "down" -> if (distance > screenHeight) (screenHeight * 0.8).toInt() else distance
                "left", "right" -> if (distance > screenWidth) (screenWidth * 0.8).toInt() else distance
                else -> distance
            }

            // 根据方向确定起始和结束坐标
            val coordinates = when (direction) {
                "up" -> {
                    val x = screenWidth / 2
                    val y = (screenHeight * 0.9).toInt()
                    arrayOf(x, y, x, y - actualDistance)
                }
                "down" -> {
                    val x = screenWidth / 2
                    val y = (screenHeight * 0.1).toInt()
                    arrayOf(x, y, x, y + actualDistance)
                }
                "left" -> {
                    val x = (screenWidth * 0.9).toInt()
                    val y = screenHeight / 2
                    arrayOf(x, y, x - actualDistance, y)
                }
                "right" -> {
                    val x = (screenWidth * 0.1).toInt()
                    val y = screenHeight / 2
                    arrayOf(x, y, x + actualDistance, y)
                }
                else -> return "错误：无效的方向"
            }

            val startX = coordinates[0]
            val startY = coordinates[1]
            val endX = coordinates[2]
            val endY = coordinates[3]

            // 使用无障碍服务执行滑动
            val success = service.performGesture(startX, startY, endX, endY, duration)
            return if (success) {
                "成功${direction}滑屏幕，实际滑动距离：$actualDistance 像素，耗时：$duration 毫秒"
            } else {
                "${direction}滑失败：无障碍服务无法执行手势"
            }

        } catch (e: Exception) {
            return "${direction}滑失败：${e.message ?: "未知错误"}"
        }
    }
}