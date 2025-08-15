package com.example.aiassistant.config

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * 全局配置中心 (单例对象)
 */
object AppConfig {

    // --- 全局可见的配置变量 ---
    lateinit var apiKey: String
    lateinit var modelName: String

    // --- 内部逻辑 ---
    private const val DEFAULT_MODEL = "qwen3-235b-a22b-instruct-2507"

    /**
     * 将监听器提升为 AppConfig 的成员变量。
     * 这会创建一个对监听器的强引用，防止它被垃圾回收。
     */
    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener

    /**
     * 初始化方法，必须在 Application.onCreate() 中调用一次。
     * @param context Application 上下文
     */
    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        // 1. 首次加载配置
        val storedApiKey = prefs.getString("key_api_key", "") ?: ""
        apiKey = if (storedApiKey.isBlank()) {
            "sk-4d0a9f150ced4e96899841b02bef1065" // 仅在用户从未设置过时使用
        } else {
            storedApiKey
        }
        modelName = prefs.getString("key_model_name", DEFAULT_MODEL) ?: DEFAULT_MODEL

        // 2. 初始化并注册监听器
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            // 当任何一个 SharedPreferences 的值改变时，这个代码块会被触发
            when (key) {
                "key_api_key" -> {
                    apiKey = sharedPreferences?.getString(key, "") ?: ""
                }
                "key_model_name" -> {
                    modelName = sharedPreferences?.getString(key, DEFAULT_MODEL) ?: DEFAULT_MODEL
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
}
