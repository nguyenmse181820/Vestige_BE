
// src/main/java/se/vestige_be/service/SimpleChatService.java
package se.vestige_be.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.response.SimpleChatRecipientResponse;
import se.vestige_be.dto.response.SimpleMessageResponse;
import se.vestige_be.pojo.Conversation;
import se.vestige_be.pojo.Message;
import se.vestige_be.pojo.User;
import se.vestige_be.repository.ConversationRepository;
import se.vestige_be.repository.MessageRepository;
import se.vestige_be.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SimpleChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    /**
     * Lấy danh sách người đã từng chat
     */
    public List<SimpleChatRecipientResponse> getChatRecipients(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Conversation> conversations = conversationRepository.findByUserOrderByLastMessageAtDesc(user);

        return conversations.stream()
                .map(conv -> {
                    // Xác định người chat (không phải current user)
                    User otherUser = conv.getBuyer().getUserId().equals(userId)
                            ? conv.getSeller()
                            : conv.getBuyer();

                    // Đếm tin nhắn chưa đọc
                    Long unreadCount = messageRepository.countUnreadInConversation(conv, user);

                    // Lấy tin nhắn cuối cùng
                    String lastMessage = null;
                    if (!conv.getMessages().isEmpty()) {
                        lastMessage = conv.getMessages().get(conv.getMessages().size() - 1).getContent();
                    }

                    return SimpleChatRecipientResponse.builder()
                            .userId(otherUser.getUserId())
                            .username(otherUser.getUsername())
                            .firstName(otherUser.getFirstName())
                            .lastName(otherUser.getLastName())
                            .profilePictureUrl(otherUser.getProfilePictureUrl())
                            .conversationId(conv.getConversationId())
                            .lastMessage(lastMessage)
                            .lastMessageAt(conv.getLastMessageAt())
                            .unreadCount(unreadCount.intValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Lấy lịch sử tin nhắn với một người
     */
    public List<SimpleMessageResponse> getMessageHistory(Long userId, Long recipientId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        // Tìm conversation giữa 2 người
        List<Conversation> conversations = conversationRepository.findConversationsBetweenUsers(user, recipient);

        if (conversations.isEmpty()) {
            return List.of(); // Chưa có conversation
        }

        Conversation conversation = conversations.get(0);
        List<Message> messages = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        return messages.stream()
                .map(SimpleMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Gửi tin nhắn
     */
    @Transactional
    public SimpleMessageResponse sendMessage(Long senderId, Long recipientId, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        // Tìm hoặc tạo conversation
        Conversation conversation = getOrCreateConversation(sender, recipient);

        // Tạo message
        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        message = messageRepository.save(message);

        // Cập nhật last message time
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return SimpleMessageResponse.fromEntity(message);
    }

    /**
     * Đánh dấu tin nhắn đã đọc
     */
    @Transactional
    public void markMessagesAsRead(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        messageRepository.markAllAsReadInConversation(conversation, user);
    }

    /**
     * Xóa lịch sử chat của user
     */
    @Transactional
    public void clearUserHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Conversation> conversations = conversationRepository.findByUserOrderByLastMessageAtDesc(user);

        for (Conversation conversation : conversations) {
            // Xóa tin nhắn của user này trong conversation
            List<Message> userMessages = messageRepository.findByConversationOrderByCreatedAtAsc(conversation)
                    .stream()
                    .filter(msg -> msg.getSender().getUserId().equals(userId))
                    .collect(Collectors.toList());

            messageRepository.deleteAll(userMessages);
        }
    }

    /**
     * Tìm hoặc tạo conversation giữa 2 users
     */
    private Conversation getOrCreateConversation(User user1, User user2) {
        List<Conversation> conversations = conversationRepository.findConversationsBetweenUsers(user1, user2);

        if (!conversations.isEmpty()) {
            return conversations.get(0);
        }

        // Tạo mới
        Conversation conversation = Conversation.builder()
                .buyer(user1)
                .seller(user2)
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build();

        return conversationRepository.save(conversation);
    }
}