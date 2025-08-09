// 文件路径: app/src/main/java/com/example/aiassistant/ui/MainActivity.kt
package com.example.aiassistant.ui

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aiassistant.ChatAdapter
import com.example.aiassistant.ChatMessage // ADDED: 导入正确的UI模型
import com.example.aiassistant.R
import com.example.aiassistant.domain.AgentExecutionBus
import com.example.aiassistant.services.AgentAccessibilityService
import com.example.aiassistant.services.AgentForegroundService
import com.example.aiassistant.utils.AccessibilityUtils
import kotlinx.coroutines.launch
import com.example.aiassistant.data.ChatMessage as ApiChatMessage // 使用别名区分API模型

class MainActivity : AppCompatActivity() {

    // --- 视图变量 ---
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var settingsButton: ImageView
    private lateinit var assistantLayout: ConstraintLayout
    private lateinit var rootContainer: View

    // --- 数据和适配器 ---
    // MODIFIED: 使用正确的UI模型 ChatMessage，以匹配ChatAdapter的构造函数
    private val messageListForAdapter = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    // --- 无障碍相关 ---
    private lateinit var enableAccessibilityButton: ImageButton
    private var accessibilityDialog: AlertDialog? = null
    private var accessibilityObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startAgentService()

        enableAccessibilityButton = findViewById(R.id.button_enable_accessibility)
        setupViews()
        setupRecyclerView()
        setupClickListeners()
        setupKeyboardListener()
        observeAgentBus()
        setupAccessibilityObserver()
    }

    private fun startAgentService() {
        val intent = Intent(this, AgentForegroundService::class.java)
        startService(intent)
    }

    private fun setupViews() {
        rootContainer = findViewById(R.id.root_container)
        assistantLayout = findViewById(R.id.assistant_layout)
        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        settingsButton = findViewById(R.id.settings_button)
    }

    private fun setupRecyclerView() {
        // MODIFIED: 现在类型匹配，此行代码正确无误
        chatAdapter = ChatAdapter(messageListForAdapter)
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        settingsButton.setOnClickListener {
            Toast.makeText(this, "设置按钮被点击", Toast.LENGTH_SHORT).show()
        }

        sendButton.setOnClickListener {
            val userInput = messageInput.text.toString().trim()
            if (userInput.isNotBlank()) {
                lifecycleScope.launch {
                    AgentExecutionBus.userMessages.emit(userInput)
                }
                messageInput.text.clear()
            }
        }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    private fun setupKeyboardListener() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboardHeight = imeInsets.bottom - systemBarInsets.bottom
            assistantLayout.translationY = -keyboardHeight.toFloat().coerceAtLeast(0f)
            insets
        }
    }

    private fun observeAgentBus() {
        lifecycleScope.launch {
            AgentExecutionBus.conversationUpdates.collect { apiMessages ->
                // 将后台的API模型(ApiChatMessage)映射为UI层的模型(ChatMessage)
                val uiMessages = apiMessages
                    .filter { it.role != "tool" && it.role != "system" }
                    .map { apiMsg ->
                        // MODIFIED: 创建正确的 ChatMessage 实例
                        ChatMessage(
                            message = apiMsg.content ?: "...",
                            isUser = apiMsg.role == "user"
                        )
                    }

                messageListForAdapter.clear()
                messageListForAdapter.addAll(uiMessages)
                chatAdapter.notifyDataSetChanged()

                if (messageListForAdapter.isNotEmpty()) {
                    chatRecyclerView.scrollToPosition(messageListForAdapter.size - 1)
                }
            }
        }
    }


    // --- 以下是无障碍服务相关的代码，保持不变 ---

    override fun onResume() {
        super.onResume()
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(this, AgentAccessibilityService::class.java)) {
            showEnableAccessibilityDialog()
        } else {
            accessibilityDialog?.dismiss()
            accessibilityDialog = null
        }
        updateAccessibilityButtonState()
    }

    override fun onDestroy() {
        super.onDestroy()
        accessibilityObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        accessibilityDialog?.dismiss()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "com.example.aiassistant.services.AgentAccessibilityService"
        val settingValue = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return settingValue?.let {
            TextUtils.SimpleStringSplitter(':').apply { setString(it) }
                .any { s -> s.equals(serviceId, ignoreCase = true) }
        } ?: false
    }

    private fun updateAccessibilityButtonState() {
        if (isAccessibilityServiceEnabled()) {
            enableAccessibilityButton.isEnabled = false
        } else {
            enableAccessibilityButton.isEnabled = true
        }
    }

    private fun showEnableAccessibilityDialog() {
        if (accessibilityDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_enable_accessibility, null)
        accessibilityDialog = AlertDialog.Builder(this, R.style.CustomDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            accessibilityDialog?.dismiss()
            accessibilityDialog = null
        }
        dialogView.findViewById<Button>(R.id.btn_go).setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
        }
        accessibilityDialog?.show()
    }

    private fun setupAccessibilityObserver() {
        accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkAndDismissDialog()
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver!!
        )
    }

    private fun checkAndDismissDialog() {
        if (isAccessibilityServiceEnabled()) {
            accessibilityDialog?.dismiss()
            accessibilityDialog = null
            updateAccessibilityButtonState()
        }
    }
}