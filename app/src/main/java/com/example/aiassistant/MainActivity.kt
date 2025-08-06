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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupRecyclerView()
        setupClickListeners()
        setupKeyboardListener()
        observeViewModel()
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
            viewModel.sendMessage(userInput)
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
}