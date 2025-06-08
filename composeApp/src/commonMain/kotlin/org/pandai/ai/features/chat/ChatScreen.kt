package org.pandai.ai.features.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
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
    var state by mutableStateOf(ChatState())

    fun init(modelPath: String) {
        viewModelScope.launch {
            state = state.copy(isLoading = true)
            val result = chatService.init(modelPath)
            if (result.isErr) {
                state = state.copy(error = result.error)
                Logger.e("Error: " + result.error)
            }
            state = state.copy(isLoading = false)
        }
    }

    fun toggleContext(value: Boolean) {
        state = state.copy(contextEnabled = value)
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        state = state.copy(
            messages = state.messages + Message(
                content = message,
                isUser = true
            ),
            isLoading = true
        )

        val lastMessages = state.messages
        viewModelScope.launch {
            chatService.sendMessage(message, state.contextEnabled).collect { result ->
                state = state.copy(
                    messages = lastMessages + Message(
                        content = result.message ?: "...",
                        isUser = false,
                        context = result.context
                    ),
                    isLoading = false
                )
            }
        }
    }
}

data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val contextEnabled: Boolean = true
)

data class Message(
    val content: String,
    val isUser: Boolean,
    val context: String? = null
)

@Serializable
data class ChatScreen(
    val modelPath: String
)


@Composable
fun ChatScreen(param: ChatScreen) {
    val viewModel: ChatViewModel = koinViewModel()
    LaunchedEffect(param.modelPath) {
        viewModel.init(param.modelPath)
    }
    ChatScreenContent(
        state = viewModel.state,
        onSendMessage = viewModel::sendMessage,
        toggleContext = viewModel::toggleContext
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    state: ChatState,
    onSendMessage: (String) -> Unit,
    toggleContext: (Boolean) -> Unit,
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
                title = { Text("Chat") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = "Context",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = state.contextEnabled,
                            onCheckedChange = { toggleContext(it) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
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
    listState: LazyListState
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
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSecondaryContainer
    var isExpanded by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Column(
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = message.content,
                color = textColor
            )

            val isHadContext = !message.context.isNullOrBlank()
            if (isHadContext) {
                Divider(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(100.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = message.context.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.animateContentSize()
                        .heightIn(max = if (isExpanded) Dp.Unspecified else 64.dp),
                    onTextLayout = { textLayoutResult = it }
                )

                if (textLayoutResult?.hasVisualOverflow == true || isExpanded) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Show Less" else "See More",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
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

@Preview
@Composable
fun ChatScreenPreview() {
    PandaiTheme {
        ChatScreenContent(
            state = ChatState(
                messages = listOf(
                    Message("Hello!", true, "User"),
                    Message("Hi there! How can I help you today?", false, "AI Assistant"),
                    Message("I have a question about the app.", true, null),
                    Message("Sure, what would you like to know?", false, "AI Assistant")
                ),
                isLoading = false
            ),
            onSendMessage = {},
            toggleContext = { }
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
                    Message("Hello!", true, "User"),
                    Message("Hi there! How can I help you today?", false, "AI Assistant"),
                    Message("I have a question about the app.", true, null)
                ),
                isLoading = true
            ),
            onSendMessage = {},
            toggleContext = {}
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
            MessageBubble(Message("This is a user message", true, "User"))
            MessageBubble(
                Message(
                    "This is an AI response",
                    false,
                    "AI Assistant\nThis is a longer context that will definitely exceed three lines of text.\n" +
                            "It contains multiple sentences and should demonstrate the height-based truncation.\n" +
                            "The text will be cut off after approximately three lines worth of height.\n" +
                            "This line should only be visible when expanded."
                )
            )
            MessageBubble(Message("This is a message without context", true, null))
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