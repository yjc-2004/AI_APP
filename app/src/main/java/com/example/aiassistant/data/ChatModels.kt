package com.example.aiassistant.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    var content: String?,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String // Arguments are a JSON string
)

@Serializable
data class ChatCompletionRequest(
    val model: String = "qwen-plus",
    val messages: List<ChatMessage>,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String = "auto"
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ChatMessage
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDescription
)

@Serializable
data class FunctionDescription(
    val name: String,
    val description: String,
    val parameters: FunctionParameters
)

@Serializable
data class FunctionParameters(
    val type: String = "object",
    val properties: Map<String, ParameterProperty>,
    val required: List<String>
)

@Serializable
data class ParameterProperty(
    val type: String,
    val description: String
)
@Serializable
data class ClickParams(val x: Int, val y: Int)

@Serializable
data class AppParams(val packageName: String)

@Serializable
data class TextParams(val text: String)

@Serializable
data class LaunchAppParams(val packageName: String)

@Serializable
data class FunctionProperty(
    val type: String,        // 参数的类型是什么？（例如："string", "number", "boolean"）
    val description: String  // 这个参数是干什么用的？（给 AI 看的文字描述）
)