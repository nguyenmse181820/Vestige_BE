package se.vestige_be.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.response.ChatMessageResponse;
import se.vestige_be.pojo.Conversation;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.chat.ChatMessage;
import se.vestige_be.pojo.enums.MessageType;
import se.vestige_be.repository.ChatMessageRepository;
import se.vestige_be.repository.ConversationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;

    @Transactional
    public ChatMessage saveMessage(Long conversationId, String senderUsername, String content, MessageType type) {
        // Find conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

        // Find sender
        User sender = userService.findByUsername(senderUsername);

        // Verify sender is participant in conversation
        if (!isUserInConversation(conversation, sender)) {
            throw new SecurityException("User is not a participant in this conversation");
        }

        // Create and save message
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .type(type)
                .isRead(false)
                .build();

        message = chatMessageRepository.save(message);

        // Update conversation's last message time
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return message;
    }

    public List<ChatMessageResponse> getConversationMessages(Long conversationId, String username) {
        // Find conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

        // Find user
        User user = userService.findByUsername(username);

        // Verify user is participant
        if (!isUserInConversation(conversation, user)) {
            throw new SecurityException("User is not a participant in this conversation");
        }

        // Get messages
        List<ChatMessage> messages = chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ChatMessageResponse> getRecentMessages(Long conversationId, String username) {
        // Find conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

        // Find user
        User user = userService.findByUsername(username);

        // Verify user is participant
        if (!isUserInConversation(conversation, user)) {
            throw new SecurityException("User is not a participant in this conversation");
        }

        // Get recent messages (last 50)
        List<ChatMessage> messages = chatMessageRepository.findTop50ByConversationOrderByCreatedAtDesc(conversation);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markMessagesAsRead(Long conversationId, String username) {
        // Find conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

        // Find user
        User user = userService.findByUsername(username);

        // Mark messages as read (only messages from other users)
        chatMessageRepository.markMessagesAsRead(conversation, user, LocalDateTime.now());
    }

    public Long getUnreadMessageCount(Long conversationId, String username) {
        // Find conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

        // Find user
        User user = userService.findByUsername(username);

        return chatMessageRepository.countUnreadMessages(conversation, user);
    }

    public List<ChatMessageResponse> searchMessages(Long conversationId, String keyword, String username) {
        // Find conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

        // Find user
        User user = userService.findByUsername(username);

        // Verify user is participant
        if (!isUserInConversation(conversation, user)) {
            throw new SecurityException("User is not a participant in this conversation");
        }

        // Search messages
        List<ChatMessage> messages = chatMessageRepository.searchInConversation(conversation, keyword);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private boolean isUserInConversation(Conversation conversation, User user) {
        return conversation.getBuyer().getUserId().equals(user.getUserId()) ||
                conversation.getSeller().getUserId().equals(user.getUserId());
    }
}