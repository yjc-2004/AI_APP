package com.example.aiassistant.data

//负责网络层
class ChatRepository {
    private val apiService = RetrofitClient.instance

    // ！！！请在这里替换为您的百炼API Key！！！
    private val apiKey = "sk-e97b135e18b542138d6313ed98f08749"

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