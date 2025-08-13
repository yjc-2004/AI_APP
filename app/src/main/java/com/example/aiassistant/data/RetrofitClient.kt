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
import java.util.concurrent.TimeUnit


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

        // --- 新增的超时设置 ---
        // 1. 读取超时：等待服务器响应数据的最长时间。这是解决模型思考时间长的关键！
        .readTimeout(60, TimeUnit.SECONDS)

        // 2. 连接超时：与服务器建立连接的最长时间。
        .connectTimeout(60, TimeUnit.SECONDS)

        // 3. 写入超时：向服务器发送请求数据的最长时间。
        .writeTimeout(60, TimeUnit.SECONDS)
        // --- 超时设置结束 ---

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