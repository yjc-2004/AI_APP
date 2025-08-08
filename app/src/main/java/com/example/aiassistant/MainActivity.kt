package com.example.aiassistant.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aiassistant.ChatAdapter
import com.example.aiassistant.R
import com.example.aiassistant.ChatMessage
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import com.example.aiassistant.utils.AccessibilityUtils
import com.example.aiassistant.services.AgentAccessibilityService
import androidx.appcompat.app.AlertDialog
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    // --- 视图变量 ---
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var settingsButton: ImageView
    private lateinit var assistantLayout: ConstraintLayout
    private lateinit var rootContainer: View

    // --- 数据和适配器 ---
    // ✅ 恢复: 列表类型恢复为 ChatMessage
    private val messageListForAdapter = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    // --- ViewModel ---
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var enableAccessibilityButton: ImageButton
    private var accessibilityDialog: AlertDialog? = null
    private var accessibilityObserver: ContentObserver? = null



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableAccessibilityButton = findViewById<ImageButton>(R.id.button_enable_accessibility)
        setupViews()
        setupRecyclerView()
        setupClickListeners()
        setupKeyboardListener()
        observeViewModel()
        setupAccessibilityObserver()

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
            viewModel.sendMessage(userInput, this@MainActivity)
            messageInput.text.clear()
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

    override fun onResume() {
        super.onResume()
  //用于关闭弹窗
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(this, AgentAccessibilityService::class.java)) {
            showEnableAccessibilityDialog()
        } else {
            accessibilityDialog?.let { dialog ->
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
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
            // 你还可以切换图片，比如 enableAccessibilityButton.setImageResource(R.drawable.ic_agent_enabled)
        } else {
            enableAccessibilityButton.isEnabled = true
            // 你还可以切换图片，比如 enableAccessibilityButton.setImageResource(R.drawable.ic_agent_disabled)
        }
    }



    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.apiMessages.collect { apiMessages ->
                val uiMessages = apiMessages
                    .filter { it.role != "tool" }
                    .map { apiMsg ->
                        // ✅ 恢复: 创建的对象类型恢复为 ChatMessage
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


    private fun showEnableAccessibilityDialog() {
        // 防止重复显示
        if (accessibilityDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_enable_accessibility, null)
        accessibilityDialog = AlertDialog.Builder(this, R.style.CustomDialog) // 保存引用
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            accessibilityDialog?.dismiss() // 使用保存的引用
            accessibilityDialog = null
        }
        dialogView.findViewById<Button>(R.id.btn_go).setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
            // 不要立即dismiss，让ContentObserver来处理
        }

        accessibilityDialog?.show() // 使用保存的引用
    }


    private fun setupAccessibilityObserver() {
        accessibilityObserver = object : ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
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

    // 新增方法：检查并关闭弹窗
    private fun checkAndDismissDialog() {
        if (isAccessibilityServiceEnabled()) {
            accessibilityDialog?.dismiss()
            accessibilityDialog = null
            updateAccessibilityButtonState()
        }
    }

}