package com.example.aiassistant.domain

import com.example.aiassistant.data.ChatMessage
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 一个全局单例的事件总线，用于在UI层 (MainActivity) 和后台服务 (AgentForegroundService) 之间安全地传递消息。
 * 这避免了直接持有对方的引用，实现了关注点分离。
 */
object AgentExecutionBus {

    /**
     * 用于从UI层向后台服务发送用户的输入文本。
     * extraBufferCapacity = 1 允许在没有订阅者时缓存最后一条消息，防止消息丢失。
     */
    val userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * 用于从后台服务向UI层广播更新后的完整对话列表。
     * replay = 1 会缓存最后一次发出的列表，确保新的订阅者（如屏幕旋转后的Activity）能立即获取当前对话状态。
     */
    val conversationUpdates = MutableSharedFlow<List<ChatMessage>>(replay = 1)
}