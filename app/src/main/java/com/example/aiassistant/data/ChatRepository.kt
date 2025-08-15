package com.example.aiassistant.data

import androidx.preference.PreferenceManager
import com.example.aiassistant.config.AppConfig

//负责网络层
class ChatRepository {
    private val apiService = RetrofitClient.instance


    private val apiKey =  AppConfig.apiKey

    suspend fun getCompletion(request: ChatCompletionRequest): Result<ChatCompletionResponse> {
        return try {
            val response = apiService.createChatCompletion(apiKey, request)
            Result.success(response)
        } catch (e: Exception) {
            // 捕获网络或解析异常
            Result.failure(e)
        }
    }
}