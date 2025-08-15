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
import android.content.Context
import com.example.aiassistant.data.AppParams
import com.example.aiassistant.data.ClickParams
import com.example.aiassistant.data.TextParams
import com.example.aiassistant.domain.SystemTools
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.*
import kotlinx.serialization.serializer
import kotlinx.serialization.Serializable
import com.example.aiassistant.domain.ScreenTools
import com.example.aiassistant.data.LaunchAppParams
import com.example.aiassistant.data.FunctionProperty

class ChatViewModel : ViewModel() {

    // 1. 初始化数据仓库，用于网络请求
    private val repository = ChatRepository()

    private val json = Json { ignoreUnknownKeys = true }
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
            ),
            Tool(
                 type = "function",
                 function = FunctionDescription(
                 name = "simulate_click",
                 description = "模拟在屏幕的指定 x 和 y 坐标上进行一次点击。坐标原点 (0,0) 在屏幕左上角。",
                 parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "x" to ParameterProperty(
                            type = "integer",
                            description = "要点击位置的 x 坐标"
                        ),
                        "y" to ParameterProperty(
                            type = "integer",
                            description = "要点击位置的 y 坐标"
                        )
                    ),
                    required = listOf("x", "y")
                )
            )
        ),

        // 3. 输入文字工具
        Tool(
            type = "function",
            function = FunctionDescription(
                name = "input_text",
                description = "在当前屏幕上拥有焦点的输入框中输入指定的文本。如果当前没有活动的输入框，则该操作可能会失败。",
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "text" to ParameterProperty(
                            type = "string",
                            description = "要输入到焦点输入框中的文字内容。"
                        )
                    ),
                    required = listOf("text")
                )
            )
        ) ,
        Tool(
            type = "function",
            function = FunctionDescription(
            name = "list_available_manuals",
            description = "获取所有可用的APP说明书列表。当用户问题可能与某个APP操作有关，但不确定是哪个APP时使用。",
            parameters = FunctionParameters(
                type = "object",
                properties = emptyMap<String, ParameterProperty>(), // 显式指定类型
                required = emptyList()
            )
            )
            ),
        Tool(
            type = "function",
            function = FunctionDescription(
                name = "get_manual_section",
                description = "从指定的说明书中，根据关键词获取特定操作或功能的详细说明章节。",
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "manual_name" to ParameterProperty(
                            type = "string",
                            description = "说明书的文件名, 例如 'photo_editor_manual.md'。必须从 list_available_manuals 工具的返回结果中选择。"
                        ),
                        "section_query" to ParameterProperty(
                            type = "string",
                            description = "描述用户想要查询的操作的关键词, 例如 '裁剪', '导出', '转换视频'。"
                        )
                    ),
                    required = listOf("manual_name", "section_query")
                )
            )
        ),

            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "observe_screen",
                    description = "分析当前手机屏幕，并以JSON格式返回所有可交互的UI元素列表。每个元素都有一个唯一的 'id'，用于后续的点击或输入操作。",
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
                    name = "click_element",
                    description = "根据 'observe_screen' 返回的元素 'id' 点击一个UI元素。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "element_id" to ParameterProperty(
                                type = "integer",
                                description = "从 'observe_screen' 获取到的目标元素的 'id'。"
                            )
                        ),
                        required = listOf("element_id")
                    )
                )
            ),
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "input_text_in_element",
                    description = "在 'observe_screen' 返回的指定 'id' 的输入框元素中输入文本。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = mapOf(
                            "element_id" to ParameterProperty(
                                type = "integer",
                                description = "从 'observe_screen' 获取到的目标输入框元素的 'id'。"
                            ),
                            "text" to ParameterProperty(
                                type = "string",
                                description = "要输入的文本内容。"
                            )
                        ),
                        required = listOf("element_id", "text")
                    )
                )
            ),
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "perform_back_press",
                    description = "执行一个全局返回操作，等同于用户点击了系统导航栏的返回按钮或使用了返回手势。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = emptyMap(), // 无需参数
                        required = emptyList()
                    )
                )
            ),
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "launch_app",
                    description = "启动一个指定的安卓应用。你需要提供该应用的标准包名。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = mapOf(
                            // 正如您所指出的，这里应该使用 ParameterProperty
                            "packageName" to ParameterProperty(
                                type = "string",
                                description = "要启动的应用的包名，例如 'com.android.settings' 或 'com.android.chrome',包名参数使用get_installed_apps工具获得所有安装包名"
                            )
                        ),
                        required = listOf("packageName")
                    )
                )
            ),
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "get_installed_apps",
                    description = "获取手机上安装的所有应用程序的包名列表",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = mapOf(),
                        required = listOf()
                    )
                )
            ),
            Tool(
                type = "function",
                function = FunctionDescription(
                    name = "return_to_home_screen",
                    description = "返回到安卓设备的主屏幕（桌面）。",
                    parameters = FunctionParameters(
                        type = "object",
                        properties = emptyMap(), // 此工具不需要参数
                        required = emptyList()
                    )
                )
            )
        )
    }


    /**
     * 当ViewModel第一次被创建时执行
     */
    init {
        val systemMessage = com.example.aiassistant.data.ChatMessage(
            role = "system",
            content = """
            你是一个名叫“小智”的个人助理。

            你的任务是
            - 帮助用户查询各种APP的操作说明书。
            工作流程指引：
            1.  当用户要你操作某个APP时，你必须首先使用 `list_available_manuals` 工具来获取当前所有可用的说明书列表。
            2.  然后，根据用户的提问和上一步返回的列表，选择一个最匹配的说明书文件名。
            3.  最后，使用 `get_manual_section` 工具并传入正确的文件名来查询具体章节。
            - 帮助用户打开各种APP
            工作流程指引：
            1. 当用户让你打开某个app,你需要先查询手机安装的所有APP,之后选择一个最匹配的app作为参数,使用app启动工具打开
            - 帮助用户完成app操作
            1,当用户要求你在app操作时,首先找到一个叫功能列表的说明书,那里有你可以实现的功能,知道功能后在相应的app说明书中查具体功能,再执行,执行时如果没有找到相应按钮,等待一段时间重新试试
            **重要规则：禁止在未确认文件存在的情况下，直接猜测并使用 `get_manual_section` 工具,所有的app操作必须基于说明书,注意说明书是给你看的,除非用户要求输出说明书,不然你就直接按照说明书执行动作**

        """.trimIndent() // <-- 使用 trimIndent() 来移除多余的格式化缩进
        )
        conversationHistory.add(systemMessage)
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
    fun sendMessage(userInput: String, context: Context) { // <--- 修改点
        // 忽略空消息
        if (userInput.isBlank()) return

        // 创建用户消息并添加到历史记录
        val userMessage = com.example.aiassistant.data.ChatMessage(role = "user", content = userInput)
        conversationHistory.add(userMessage)

        // 更新UI状态，立即显示用户发送的消息
        _apiMessages.value = conversationHistory.toList()

        // 启动一个后台协程来处理与模型的交互，避免阻塞主线程
        viewModelScope.launch {
            processConversation(context) // <--- 修改点
        }
    }

    /**
     * 处理与大模型API的单次请求和响应
     */
    private suspend fun processConversation(context: Context) {
        val request = ChatCompletionRequest(
            model = "qwen3-235b-a22b-instruct-2507",
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
                    handleToolCalls(assistantMessage.toolCalls,context)
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



    @Serializable
    data class ElementClickParams(val element_id: Int)

    @Serializable
    data class ElementInputParams(val element_id: Int, val text: String)



    /**
     * 当模型请求调用工具时执行
     */
    private suspend fun handleToolCalls(toolCalls: List<ToolCall>, context: Context) {
        // 遍历模型请求的所有工具调用
        for (toolCall in toolCalls) {
            // 根据函数名调用对应的本地Kotlin函数
            val toolResultContent = when (toolCall.function.name) {
                // 已有工具
                "get_current_time" -> getCurrentTime()
                "get_current_weather" -> {
                    // 假设您有一个LocationParams数据类用于解析
                    // val params = json.decodeFromString<LocationParams>(toolCall.function.arguments)
                    // getCurrentWeather(params.location)
                    // 为了简单起见，如果您的getCurrentWeather已经处理了JSON字符串，可以直接调用
                    getCurrentWeather(toolCall.function.arguments)
                }

                // 新增的系统工具
                "simulate_click" -> {
                    val params = json.decodeFromString<ClickParams>(toolCall.function.arguments)
                    SystemTools.simulateClick(params.x, params.y)
                }
                "input_text" -> {
                    val params = json.decodeFromString<TextParams>(toolCall.function.arguments)
                    SystemTools.inputText(params.text)
                }
                "list_available_manuals" -> {
                    listManualsFromAssets(context)
                }
                "get_manual_section" -> {
                    @Serializable data class Params(val manual_name: String, val section_query: String)
                    val params = json.decodeFromString<Params>(toolCall.function.arguments)
                    getSectionFromManual(context, params.manual_name, params.section_query)
                }
                "observe_screen" -> {
                    ScreenTools.analyzeScreen()
                }
                "click_element" -> {
                    val params = json.decodeFromString<ElementClickParams>(toolCall.function.arguments)
                    ScreenTools.clickElementById(params.element_id)
                }
                "input_text_in_element" -> {
                    val params =
                        json.decodeFromString<ElementInputParams>(toolCall.function.arguments)
                    ScreenTools.inputTextInElementById(params.element_id, params.text)
                }
                "perform_back_press" -> {
                    SystemTools.performBackPress()
                }
                "launch_app" -> {
                    try {
                        // 解析LLM传来的JSON参数
                        val params =
                            json.decodeFromString<LaunchAppParams>(toolCall.function.arguments)
                        // 调用您在第一步中创建的函数
                        SystemTools.launchApp(context, params.packageName)
                    } catch (e: Exception) {
                        "错误：解析参数失败 - ${e.message}"
                    }
                }
                "get_installed_apps" -> {
                    // 这个工具不需要参数，直接调用即可
                    SystemTools.getInstalledApps(context)
                }
                "return_to_home_screen" -> {
                    // 调用在第一步中创建的函数
                    SystemTools.returnToHomeScreen(context)
                }
                    else -> "错误：未知的工具 ${toolCall.function.name}"
            }

            // 将工具的执行结果封装成一条 "tool" 角色的消息
            val toolMessage = com.example.aiassistant.data.ChatMessage(
                role = "tool",
                content = toolResultContent,
                toolCallId = toolCall.id // 必须包含tool_call_id以对应请求
            )
            conversationHistory.add(toolMessage)
        }

        // 更新UI以显示工具消息（可选，通常不显示）
        _apiMessages.value = conversationHistory.toList()

        // 在所有工具都执行完毕后，再次调用模型，让模型根据所有工具结果生成最终回复
        processConversation(context)
    }


    /**
     * 解析Markdown文件，将其内容按二级标题 (##) 分割成章节Map.
     * @param markdownContent 完整的md文件内容.
     * @return 一个Map，Key是章节标题，Value是该章节的完整内容.
     */
    fun parseMarkdownManual(markdownContent: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        val lines = markdownContent.lines()
        var currentSectionTitle: String? = null
        val currentSectionContent = StringBuilder()

        for (line in lines) {
            if (line.startsWith("## ")) {
                if (currentSectionTitle != null) {
                    sections[currentSectionTitle] = currentSectionContent.toString().trim()
                }
                currentSectionTitle = line.substring(3).trim()
                currentSectionContent.clear()
            } else {
                currentSectionContent.append(line).append("\n")
            }
        }

        if (currentSectionTitle != null) {
            sections[currentSectionTitle] = currentSectionContent.toString().trim()
        }

        return sections
    }



    private fun listManualsFromAssets(context: Context): String {
        return try {
            val manuals = context.assets.list("manuals")?.toList() ?: emptyList()
            Json.encodeToString(ListSerializer(String.serializer()), manuals)
        } catch (e: IOException) {
            "错误: 无法读取说明书列表。"
        }
    }

    private fun getSectionFromManual(context: Context, manualName: String, query: String): String {
        return try {
            // 1. 读取并解析指定的说明书
            val markdownContent = context.assets.open("manuals/$manualName").bufferedReader().use { it.readText() }
            val sections = parseMarkdownManual(markdownContent)

            // 2. 查找最相关的章节 (这里使用简单的关键词匹配)
            val bestMatch = sections.entries.find { (title, _) ->
                title.contains(query, ignoreCase = true)
            }

            if (bestMatch != null) {
                // 找到了，返回章节内容
                "从 '$manualName' 中找到相关章节:\n\n${bestMatch.value}"
            } else {
                // 没找到
                "在 '$manualName' 中未找到关于 '$query' 的章节。可用的章节标题有: ${sections.keys.joinToString()}"
            }
        } catch (e: IOException) {
            "错误: 无法读取或解析说明书 '$manualName'。"
        }
    }
}