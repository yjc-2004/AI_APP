// 文件路径: app/src/main/java/com/example/aiassistant/ui/ChatViewModel.kt
package com.example.aiassistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiassistant.data.ChatCompletionRequest
import com.example.aiassistant.data.ChatRepository
import com.example.aiassistant.data.FunctionDescription
import com.example.aiassistant.data.FunctionParameters
import com.example.aiassistant.data.ParameterProperty
import com.example.aiassistant.data.Tool
import com.example.aiassistant.data.ToolCall
import com.example.aiassistant.domain.LocalTools.getCurrentTime
import com.example.aiassistant.domain.LocalTools.getCurrentWeather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // 1. 初始化数据仓库，用于网络请求
    private val repository = ChatRepository()

    // 2. 内部状态，使用为API定义的复杂ChatMessage模型 (com.example.aiassistant.data.ChatMessage)
    private val _apiMessages = MutableStateFlow<List<com.example.aiassistant.data.ChatMessage>>(emptyList())
    // 3. 暴露给UI (MainActivity) 的只读状态流
    val apiMessages = _apiMessages.asStateFlow()

    // 4. 维护完整的对话历史，包括用户、助手和工具的交互
    private val conversationHistory = mutableListOf<com.example.aiassistant.data.ChatMessage>()

    // 5. 定义模型可用的工具列表
    private val availableTools by lazy {
        listOf(
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "get_current_time",
                    description = "当你想知道现在的时间时非常有用。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = emptyMap(),
                        required = emptyList()
                    )
                )
            ),
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "get_current_weather",
                    description = "当你想查询指定城市的天气时非常有用。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "location" to ParameterProperty(
                                type = "string",
                                description = "城市或县区，比如北京市、杭州市、余杭区等。"
                            )
                        ),
                        required = listOf("location")
                    )
                )
            )
        )
    }


    /**
     * 当ViewModel第一次被创建时执行
     */
    init {
        // 添加初始的欢迎语
        conversationHistory.add(
            com.example.aiassistant.data.ChatMessage(
                role = "assistant",
                content = "你好，有什么可以帮你的吗？",
                toolCalls = null
            )
        )
        // 更新状态流，让UI显示欢迎语
        _apiMessages.value = conversationHistory.toList()
    }

    /**
     * 从UI调用的主要方法，用于发送新消息
     */
    fun sendMessage(userInput: String) {
        // 忽略空消息
        if (userInput.isBlank()) return

        // 创建用户消息并添加到历史记录
        val userMessage = com.example.aiassistant.data.ChatMessage(role = "user", content = userInput)
        conversationHistory.add(userMessage)

        // 更新UI状态，立即显示用户发送的消息
        _apiMessages.value = conversationHistory.toList()

        // 启动一个后台协程来处理与模型的交互，避免阻塞主线程
        viewModelScope.launch {
            processConversation()
        }
    }

    /**
     * 处理与大模型API的单次请求和响应
     */
    private suspend fun processConversation() {
        val request = ChatCompletionRequest(
            model = "qwen-max",
            messages = conversationHistory, // 发送完整对话历史
            tools = availableTools // 告知模型它能使用哪些工具
        )

        // 调用Repository执行网络请求
        val result = repository.getCompletion(request)

        result.fold(
            onSuccess = { response ->
                // 请求成功，获取助手的回复
                val assistantMessage = response.choices.first().message
                conversationHistory.add(assistantMessage)

                // 检查助手回复是否要求调用工具
                if (assistantMessage.toolCalls.isNullOrEmpty()) {
                    // 无需调用工具，对话结束，更新UI
                    _apiMessages.value = conversationHistory.toList()
                } else {
                    // 需要调用工具，先更新UI显示模型的思考过程（可选）
                    _apiMessages.value = conversationHistory.toList()
                    // 执行工具调用，并在完成后再次调用processConversation
                    handleToolCalls(assistantMessage.toolCalls)
                }
            },
            onFailure = { error ->
                // 请求失败，构造一条错误消息并更新UI
                val errorMessage = com.example.aiassistant.data.ChatMessage("assistant", "抱歉，网络出错了: ${error.message}", null)
                conversationHistory.add(errorMessage)
                _apiMessages.value = conversationHistory.toList()
            }
        )
    }

    /**
     * 当模型请求调用工具时执行
     */
    private suspend fun handleToolCalls(toolCalls: List<ToolCall>) {
        // (当前范例只处理第一个工具调用请求)
        val toolCall = toolCalls.first()

        // 根据函数名调用对应的本地Kotlin函数
        val toolResultContent = when (toolCall.function.name) {
            "get_current_weather" -> getCurrentWeather(toolCall.function.arguments)
            "get_current_time" -> getCurrentTime()
            else -> "错误：未知的工具"
        }

        // 将工具的执行结果封装成一条 "tool" 角色的消息
        val toolMessage = com.example.aiassistant.data.ChatMessage(
            role = "tool",
            content = toolResultContent,
            toolCallId = toolCall.id // 必须包含tool_call_id以对应请求
        )
        conversationHistory.add(toolMessage)

        // 更新UI以显示工具消息（可选，通常不显示）
        _apiMessages.value = conversationHistory.toList()

        // 再次调用模型，并传入工具执行结果，让模型根据工具结果生成最终回复
        processConversation()
    }
}