// src/main/java/se/vestige_be/service/SimpleChatService.java
package se.vestige_be.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SimpleChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    /**
     * Lấy tất cả users có thể chat (trừ current user)
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsersExcept(Long currentUserId) {
        log.info("Getting all users except userId: {}", currentUserId);

        List<User> allUsers = userRepository.findAll();
        log.info("Total users in database: {}", allUsers.size());

        List<User> filteredUsers = allUsers.stream()
                .filter(user -> !user.getUserId().equals(currentUserId))
                .collect(Collectors.toList());

        log.info("Users after filtering (excluding current user): {}", filteredUsers.size());

        // Log user details for debugging
        filteredUsers.forEach(user ->
                log.info("User: ID={}, username={}, firstName={}, lastName={}",
                        user.getUserId(), user.getUsername(), user.getFirstName(), user.getLastName())
        );

        return filteredUsers;
    }

    /**
     * Tạo hoặc lấy conversation giữa 2 users
     */
    @Transactional
    public Conversation getOrCreateConversation(Long userId, Long recipientId) {
        log.info("Getting or creating conversation between userId: {} and recipientId: {}", userId, recipientId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found with ID: " + recipientId));

        log.info("Found user: {} and recipient: {}", user.getUsername(), recipient.getUsername());

        return getOrCreateConversation(user, recipient);
    }

    /**
     * Lấy danh sách người đã từng chat - COMPLETELY AVOID accessing messages collection
     */
    @Transactional(readOnly = true)
    public List<SimpleChatRecipientResponse> getChatRecipients(Long userId) {
        log.info("Getting chat recipients for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Conversation> conversations = conversationRepository.findByUserOrderByLastMessageAtDesc(user);
        log.info("Found {} conversations for user: {}", conversations.size(), user.getUsername());

        return conversations.stream()
                .map(conv -> {
                    try {
                        // Xác định người chat (không phải current user)
                        User otherUser = conv.getBuyer().getUserId().equals(userId)
                                ? conv.getSeller()
                                : conv.getBuyer();

                        // Đếm tin nhắn chưa đọc using separate query
                        Long unreadCount = messageRepository.countUnreadInConversation(conv, user);

                        // Get last message using separate query - NEVER access conv.getMessages()
                        String lastMessage = null;
                        LocalDateTime lastMessageAt = conv.getLastMessageAt();

                        List<Message> recentMessages = messageRepository.findTop1ByConversationOrderByCreatedAtDesc(conv);
                        if (!recentMessages.isEmpty()) {
                            Message lastMsg = recentMessages.get(0);
                            lastMessage = lastMsg.getContent();
                            lastMessageAt = lastMsg.getCreatedAt();
                        }

                        return SimpleChatRecipientResponse.builder()
                                .userId(otherUser.getUserId())
                                .username(otherUser.getUsername() != null ? otherUser.getUsername() : "")
                                .firstName(otherUser.getFirstName() != null ? otherUser.getFirstName() : "")
                                .lastName(otherUser.getLastName() != null ? otherUser.getLastName() : "")
                                .profilePictureUrl(otherUser.getProfilePictureUrl())
                                .conversationId(conv.getConversationId())
                                .lastMessage(lastMessage != null ? lastMessage : "No messages yet")
                                .lastMessageAt(lastMessageAt)
                                .unreadCount(unreadCount != null ? unreadCount.intValue() : 0)
                                .build();
                    } catch (Exception e) {
                        log.error("Error processing conversation {}: {}", conv.getConversationId(), e.getMessage());
                        // Return minimal data if there's an error
                        User otherUser = conv.getBuyer().getUserId().equals(userId)
                                ? conv.getSeller()
                                : conv.getBuyer();

                        return SimpleChatRecipientResponse.builder()
                                .userId(otherUser.getUserId())
                                .username(otherUser.getUsername() != null ? otherUser.getUsername() : "")
                                .firstName(otherUser.getFirstName() != null ? otherUser.getFirstName() : "")
                                .lastName(otherUser.getLastName() != null ? otherUser.getLastName() : "")
                                .conversationId(conv.getConversationId())
                                .lastMessage("Error loading message")
                                .lastMessageAt(conv.getLastMessageAt())
                                .unreadCount(0)
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Lấy lịch sử tin nhắn với một người
     */
    @Transactional(readOnly = true)
    public List<SimpleMessageResponse> getMessageHistory(Long userId, Long recipientId) {
        log.info("Getting message history between userId: {} and recipientId: {}", userId, recipientId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        // Tìm conversation giữa 2 người
        List<Conversation> conversations = conversationRepository.findConversationsBetweenUsers(user, recipient);

        if (conversations.isEmpty()) {
            log.info("No conversation found between users");
            return List.of(); // Chưa có conversation
        }

        Conversation conversation = conversations.get(0);
        List<Message> messages = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        log.info("Found {} messages in conversation", messages.size());

        return messages.stream()
                .map(SimpleMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Gửi tin nhắn
     */
    @Transactional
    public SimpleMessageResponse sendMessage(Long senderId, Long recipientId, String content) {
        log.info("Sending message from userId: {} to recipientId: {}", senderId, recipientId);

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
        log.info("Message saved with ID: {}", message.getMessageId());

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
        log.info("Marking messages as read for conversationId: {} by userId: {}", conversationId, userId);

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
        log.info("Clearing chat history for userId: {}", userId);

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
        log.info("Getting or creating conversation between {} and {}", user1.getUsername(), user2.getUsername());

        List<Conversation> conversations = conversationRepository.findConversationsBetweenUsers(user1, user2);

        if (!conversations.isEmpty()) {
            log.info("Found existing conversation with ID: {}", conversations.get(0).getConversationId());
            return conversations.get(0);
        }

        // Tạo mới - quy ước: user có ID nhỏ hơn làm buyer, user có ID lớn hơn làm seller
        User buyer = user1.getUserId() < user2.getUserId() ? user1 : user2;
        User seller = user1.getUserId() < user2.getUserId() ? user2 : user1;

        Conversation conversation = Conversation.builder()
                .buyer(buyer)
                .seller(seller)
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build();

        conversation = conversationRepository.save(conversation);
        log.info("Created new conversation with ID: {}", conversation.getConversationId());

        return conversation;
    }
}