package com.example.feature.chatbot.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.chatbot.api.ChatMessage
import com.example.feature.chatbot.api.ChatSession
import com.example.feature.chatbot.api.ChatbotApi
import com.example.feature.chatbot.api.ChatbotApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatbotViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    var errorMessage = MutableStateFlow<String?>(null)
        private set

    var scrollIndex: Int = -1
        private set
    var scrollOffset: Int = 0
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }

    private var isInitialized = false
    private var lastToken: String? = null

    fun clearUserData() {
        isInitialized = false
        lastToken = null
        _messages.value = emptyList()
        _sessions.value = emptyList()
        _currentSessionId.value = null
        _isLoading.value = false
        _isSending.value = false
        errorMessage.value = null
        scrollIndex = -1
        scrollOffset = 0
    }

    fun loadInitialHistory(token: String, onUnauthorized: () -> Unit) {
        if (lastToken != null && lastToken != token) {
            clearUserData()
        }
        lastToken = token

        if (isInitialized || _messages.value.isNotEmpty()) return
        isInitialized = true

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val fetched = ChatbotApi.getSessions(token)
                _sessions.value = fetched

                val activeSession = if (!_currentSessionId.value.isNullOrEmpty()) {
                    fetched.find { it.id == _currentSessionId.value }
                } else {
                    fetched.firstOrNull { it.status == "active" } ?: fetched.firstOrNull()
                }

                if (activeSession != null) {
                    _currentSessionId.value = activeSession.id
                    val history = ChatbotApi.getMessages(token, activeSession.id)
                    _messages.value = history
                }
            } catch (e: Exception) {
                if (e is ChatbotApiException && e.statusCode == 401) {
                    onUnauthorized()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSessions(token: String, onUnauthorized: () -> Unit) {
        viewModelScope.launch {
            try {
                val fetched = ChatbotApi.getSessions(token)
                _sessions.value = fetched
            } catch (e: Exception) {
                handleError(e, "Failed to load session list", onUnauthorized)
            }
        }
    }

    fun startNewChat(token: String, onUnauthorized: () -> Unit) {
        viewModelScope.launch {
            if (!_currentSessionId.value.isNullOrEmpty()) {
                try {
                    ChatbotApi.endSession(token, _currentSessionId.value!!)
                } catch (e: Exception) {
                    if (e is ChatbotApiException && e.statusCode == 401) {
                        onUnauthorized()
                    }
                }
            }
            _currentSessionId.value = null
            _messages.value = emptyList()
            errorMessage.value = null
        }
    }

    fun loadSession(token: String, sessionId: String, onUnauthorized: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val history = ChatbotApi.getMessages(token, sessionId)
                _messages.value = history
                _currentSessionId.value = sessionId
                errorMessage.value = null
            } catch (e: Exception) {
                handleError(e, "Failed to load chat history", onUnauthorized)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendMessage(token: String, text: String, onUnauthorized: () -> Unit) {
        val userMsgText = text.trim()
        if (userMsgText.isEmpty() || _isSending.value) return

        errorMessage.value = null

        val tempUserMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = userMsgText,
            createdAt = "",
        )
        _messages.value = _messages.value + tempUserMsg
        _isSending.value = true

        viewModelScope.launch {
            try {
                val response = ChatbotApi.sendMessage(token, userMsgText, _currentSessionId.value)
                _currentSessionId.value = response.sessionId

                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = response.reply,
                    createdAt = "",
                )
                _messages.value = _messages.value + assistantMsg
            } catch (e: Exception) {
                handleError(e, "Could not send message", onUnauthorized)
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteSession(token: String, session: ChatSession, onUnauthorized: () -> Unit) {
        viewModelScope.launch {
            try {
                ChatbotApi.deleteSession(token, session.id)
                _sessions.value = _sessions.value.filter { it.id != session.id }
                if (_currentSessionId.value == session.id) {
                    startNewChat(token, onUnauthorized)
                }
            } catch (e: Exception) {
                handleError(e, "Could not delete session", onUnauthorized)
            }
        }
    }

    private fun handleError(e: Exception, fallbackMsg: String, onUnauthorized: () -> Unit) {
        if (e is ChatbotApiException && e.statusCode == 401) {
            onUnauthorized()
        } else {
            errorMessage.value = e.message ?: fallbackMsg
        }
    }
}
