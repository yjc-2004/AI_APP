package com.example.aiassistant.domain.LocalTools

//本地工具包
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
private data class WeatherArguments(val location: String)

private val json = Json { ignoreUnknownKeys = true }

fun getCurrentWeather(argumentsJson: String): String {
    return try {
        val arguments = json.decodeFromString<WeatherArguments>(argumentsJson)
        val weatherConditions = listOf("晴天", "多云", "小雨", "雷阵雨")
        "${arguments.location}今天是${weatherConditions.random()}。"
    } catch (e: Exception) {
        "无法解析地点参数"
    }
}

fun getCurrentTime(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return "当前时间是：${formatter.format(Date())}"
}