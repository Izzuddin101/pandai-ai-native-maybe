package org.pandai.ai.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.android.annotation.KoinViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.pandai.ai.services.PandaiAIChat
import org.pandai.ai.ui.PandaiTheme

@KoinViewModel
class ChatViewModel(
    private val chatService: PandaiAIChat
) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            // Add user message
            _state.value = _state.value.copy(
                messages = _state.value.messages + Message(
                    content = message,
                    isUser = true
                ),
                isLoading = true
            )

            // Get AI response
            chatService.sendMessage(message).collect { result ->
                _state.value = _state.value.copy(
                    messages = _state.value.messages + Message(
                        content = result.message ?: "No response",
                        isUser = false
                    ),
                    isLoading = false
                )
            }
        }
    }
}

data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false
)

data class Message(
    val content: String,
    val isUser: Boolean
)

@Serializable
data class ChatScreen(
    val modelId: String
)


@Composable
fun ChatScreen() {
    val viewModel: ChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    ChatScreenContent(
        state = state,
        onSendMessage = viewModel::sendMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    state: ChatState,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ChatMessageList(
                messages = state.messages,
                isLoading = state.isLoading,
                listState = listState
            )
            ChatInput(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSendMessage = {
                    onSendMessage(messageText)
                    messageText = ""
                },
                isLoading = state.isLoading
            )
        }
    }
}

@Composable
fun ColumnScope.ChatMessageList(
    messages: List<Message>,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages) { message ->
            MessageBubble(message)
        }
        if (isLoading) {
            item {
                LoadingIndicator()
            }
        }
    }
}

@Composable
fun ChatInput(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            placeholder = { Text("Type a message...") },
            enabled = !isLoading
        )
        IconButton(
            onClick = onSendMessage,
            enabled = messageText.isNotBlank() && !isLoading
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send message"
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (message.isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 2.dp
            )
        }
    }
}

@Preview()
@Composable
fun ChatScreenPreview() {
    PandaiTheme {
        ChatScreenContent(
            state = ChatState(
                messages = listOf(
                    Message("Hello!", true),
                    Message("Hi there! How can I help you today?", false),
                    Message("I have a question about the app.", true)
                ),
                isLoading = false
            ),
            onSendMessage = {}
        )
    }
}

@Preview()
@Composable
fun ChatScreenLoadingPreview() {
    PandaiTheme {
        ChatScreenContent(
            state = ChatState(
                messages = listOf(
                    Message("Hello!", true),
                    Message("Hi there! How can I help you today?", false),
                    Message("I have a question about the app.", true)
                ),
                isLoading = true
            ),
            onSendMessage = {}
        )
    }
}

@Preview()
@Composable
fun MessageBubblePreview() {
    PandaiTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageBubble(Message("This is a user message", true))
            MessageBubble(Message("This is an AI response", false))
        }
    }
}

@Preview()
@Composable
fun ChatInputPreview() {
    PandaiTheme {
        ChatInput(
            messageText = "Hello",
            onMessageTextChange = {},
            onSendMessage = {},
            isLoading = false
        )
    }
}

@Preview()
@Composable
fun LoadingIndicatorPreview() {
    PandaiTheme {
        LoadingIndicator()
    }
}