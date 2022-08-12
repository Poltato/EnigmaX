package io.github.zohrevand.dialogue.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zohrevand.core.model.data.ChatState
import io.github.zohrevand.core.model.data.ChatState.Active
import io.github.zohrevand.core.model.data.ChatState.Composing
import io.github.zohrevand.core.model.data.ChatState.Paused
import io.github.zohrevand.core.model.data.Conversation
import io.github.zohrevand.core.model.data.ConversationStatus.Started
import io.github.zohrevand.core.model.data.Message
import io.github.zohrevand.core.model.data.SendingChatState
import io.github.zohrevand.dialogue.core.data.repository.ConversationsRepository
import io.github.zohrevand.dialogue.core.data.repository.MessagesRepository
import io.github.zohrevand.dialogue.core.data.repository.SendingChatStatesRepository
import io.github.zohrevand.dialogue.feature.chat.ChatUiState.Loading
import io.github.zohrevand.dialogue.feature.chat.ChatUiState.Success
import io.github.zohrevand.dialogue.feature.chat.navigation.ChatDestination
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversationsRepository: ConversationsRepository,
    private val messagesRepository: MessagesRepository,
    private val sendingChatStatesRepository: SendingChatStatesRepository
) : ViewModel() {

    private val contactId: String = checkNotNull(
        savedStateHandle[ChatDestination.contactJidArg]
    )

    private val conversation = conversationsRepository.getConversation(peerJid = contactId)

    private val messages = messagesRepository.getMessagesStream(peerJid = contactId)

    private var currentChatState = CurrentChatState(Active)

    init {
        viewModelScope.launch {
            sendChatState(Active)
            resetUnreadMessageCount()
        }
    }

    val uiState: StateFlow<ChatUiState> =
        combine(
            conversation,
            messages
        ) { conversation, messages ->
            if (conversation != null) {
                Success(conversation, messages)
            } else {
                conversationsRepository.updateConversation(
                    Conversation(
                        peerJid = contactId,
                        status = Started
                    )
                )
                Loading
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading
            )

    fun sendMessage(text: String) {
        currentChatState.cancelSendingPausedState()
        viewModelScope.launch {
            messagesRepository.updateMessage(
                Message.create(text, contactId)
            )

            updateDraft(null)
        }
    }

    fun userTyping(messageText: String) {
        updateDraft(messageText)

        currentChatState.cancelSendingPausedState()
        val sendingPausedStateJob = viewModelScope.launch {
            delay(3_000)
            sendChatState(Paused)
        }
        currentChatState = currentChatState.copy(sendingPausedStateJob = sendingPausedStateJob)

        if (currentChatState.shouldSendComposing()) {
            viewModelScope.launch {
                sendChatState(Composing)
            }
        }
    }

    private suspend fun sendChatState(chatState: ChatState) {
        currentChatState = currentChatState.copy(chatState = chatState)
        sendingChatStatesRepository.updateSendingChatState(
            SendingChatState(peerJid = contactId, chatState = chatState)
        )
    }

    private suspend fun resetUnreadMessageCount() {
        conversation.first()?.let {
            conversationsRepository.updateConversation(it.copy(unreadMessagesCount = 0))
        }
    }

    private fun updateDraft(messageText: String?) {
        viewModelScope.launch {
            conversation.first()?.let {
                conversationsRepository.updateConversation(it.copy(draftMessage = messageText))
            }
        }
    }
}

data class CurrentChatState(
    val chatState: ChatState,
    val sendingPausedStateJob: Job? = null
) {
    fun cancelSendingPausedState() {
        sendingPausedStateJob?.cancel()
    }

    fun shouldSendComposing() = chatState != Composing
}

sealed interface ChatUiState {
    data class Success(val conversation: Conversation, val messages: List<Message>) : ChatUiState
    object Loading : ChatUiState
}
