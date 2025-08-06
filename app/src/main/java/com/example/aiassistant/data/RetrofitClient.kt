package com.example.aiassistant.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST


object RetrofitClient {
    private const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"

    // 配置Json解析器，忽略未知的key
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // 创建OkHttp客户端并添加日志拦截器，方便调试
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val instance: DashScopeApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(DashScopeApiService::class.java)
    }
}

interface DashScopeApiService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse // 直接返回主体，简化错误处理
}