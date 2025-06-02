package se.vestige_be.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import se.vestige_be.service.SimpleChatService;
import se.vestige_be.service.UserService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class SimpleChatHandlerConfig implements WebSocketHandler {

    private final SimpleChatService chatService;
    private final UserService userService;
    private final ObjectMapper objectMapper; // Inject ObjectMapper được cấu hình

    // Lưu sessions theo userId
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public SimpleChatHandlerConfig(SimpleChatService chatService,
                                   UserService userService,
                                   ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.userService = userService;
        this.objectMapper = objectMapper; // Sử dụng ObjectMapper đã được cấu hình
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());

        // Lấy userId từ query parameter
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("User {} connected with session {}", userId, session.getId());

            // Gửi confirmation ngay lập tức
            sendToSession(session, Map.of(
                    "action", "CONNECTION_ESTABLISHED",
                    "userId", userId,
                    "message", "Connected successfully"
            ));
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String userId = getUserIdFromSession(session);
        if (userId == null) {
            sendError(session, "User not authenticated");
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getPayload().toString(), Map.class);
            String action = (String) payload.get("action");

            log.info("Received action: {} from user: {}", action, userId);

            // Xử lý bất đồng bộ để không block WebSocket thread
            CompletableFuture.runAsync(() -> {
                try {
                    switch (action) {
                        case "GET_RECIPIENTS":
                            handleGetRecipients(session, userId);
                            break;
                        case "GET_ALL_USERS":
                            handleGetAllUsers(session, userId);
                            break;
                        case "GET_HISTORY":
                            handleGetHistory(session, userId, payload);
                            break;
                        case "SEND_MESSAGE":
                            handleSendMessage(userId, payload);
                            break;
                        case "START_CONVERSATION":
                            handleStartConversation(session, userId, payload);
                            break;
                        case "JOIN_CONVERSATION":
                            handleJoinConversation(session, userId, payload);
                            break;
                        default:
                            sendError(session, "Unknown action: " + action);
                    }
                } catch (Exception e) {
                    log.error("Error handling action {} from user {}: {}", action, userId, e.getMessage(), e);
                    sendError(session, "Error processing message: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Error parsing message from user {}: {}", userId, e.getMessage(), e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("User {} disconnected", userId);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    // Handle actions
    private void handleGetRecipients(WebSocketSession session, String userId) {
        try {
            log.info("Getting recipients for user: {}", userId);
            var recipients = chatService.getChatRecipients(Long.parseLong(userId));
            log.info("Found {} recipients", recipients.size());

            sendToSession(session, Map.of(
                    "action", "RECIPIENTS_RESPONSE",
                    "recipients", recipients
            ));
        } catch (Exception e) {
            log.error("ERROR getting recipients for user {}: {}", userId, e.getMessage(), e);
            sendError(session, "Error getting recipients: " + e.getMessage());
        }
    }

    private void handleGetAllUsers(WebSocketSession session, String userId) {
        try {
            log.info("Getting all users except user: {}", userId);

            var allUsers = userService.findAllUsers();
            var filteredUsers = allUsers.stream()
                    .filter(user -> !user.getUserId().equals(Long.parseLong(userId)))
                    .map(user -> Map.of(
                            "userId", user.getUserId(),
                            "username", user.getUsername(),
                            "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                            "lastName", user.getLastName() != null ? user.getLastName() : "",
                            "profilePictureUrl", user.getProfilePictureUrl() != null ? user.getProfilePictureUrl() : ""
                    ))
                    .toList();

            sendToSession(session, Map.of(
                    "action", "ALL_USERS_RESPONSE",
                    "users", filteredUsers
            ));
        } catch (Exception e) {
            log.error("Error getting all users for user {}: {}", userId, e.getMessage(), e);
            sendError(session, "Error getting users: " + e.getMessage());
        }
    }

    private void handleStartConversation(WebSocketSession session, String userId, Map<String, Object> payload) {
        try {
            Long recipientId = Long.parseLong(payload.get("recipientId").toString());
            log.info("Starting conversation between user {} and recipient {}", userId, recipientId);

            var conversation = chatService.getOrCreateConversation(Long.parseLong(userId), recipientId);

            sendToSession(session, Map.of(
                    "action", "CONVERSATION_STARTED",
                    "conversationId", conversation.getConversationId(),
                    "recipientId", recipientId
            ));
        } catch (Exception e) {
            log.error("Error starting conversation for user {}: {}", userId, e.getMessage(), e);
            sendError(session, "Error starting conversation: " + e.getMessage());
        }
    }

    private void handleGetHistory(WebSocketSession session, String userId, Map<String, Object> payload) {
        try {
            Long recipientId = Long.parseLong(payload.get("recipientId").toString());
            log.info("Getting history between user {} and recipient {}", userId, recipientId);

            var history = chatService.getMessageHistory(Long.parseLong(userId), recipientId);
            log.info("Found {} messages in history", history.size());

            sendToSession(session, Map.of(
                    "action", "HISTORY_RESPONSE",
                    "recipientId", recipientId,
                    "messages", history
            ));

        } catch (Exception e) {
            log.error("ERROR getting history for user {}: {}", userId, e.getMessage(), e);
            sendError(session, "Error getting history: " + e.getMessage());
        }
    }

    private void handleSendMessage(String senderId, Map<String, Object> payload) {
        try {
            Long senderIdLong = Long.parseLong(senderId);
            Long recipientId = Long.parseLong(payload.get("recipientId").toString());
            String content = payload.get("content").toString();

            log.info("Sending message from {} to {}: {}", senderId, recipientId, content);

            // Lưu message vào DB
            var messageResponse = chatService.sendMessage(senderIdLong, recipientId, content);

            // QUAN TRỌNG: Gửi message cho cả người gửi và người nhận NGAY LẬP TỨC

            // Gửi cho người nhận
            WebSocketSession recipientSession = userSessions.get(recipientId.toString());
            if (recipientSession != null && recipientSession.isOpen()) {
                sendToSession(recipientSession, Map.of(
                        "action", "NEW_MESSAGE",
                        "message", messageResponse
                ));
                log.info("Message sent to recipient {}", recipientId);
            } else {
                log.info("Recipient {} is not online", recipientId);
            }

            // Confirm cho người gửi NGAY LẬP TỨC
            WebSocketSession senderSession = userSessions.get(senderId);
            if (senderSession != null && senderSession.isOpen()) {
                sendToSession(senderSession, Map.of(
                        "action", "MESSAGE_SENT",
                        "message", messageResponse
                ));
                log.info("Message confirmation sent to sender {}", senderId);
            }

        } catch (Exception e) {
            log.error("Error sending message from user {}: {}", senderId, e.getMessage(), e);
            // Thông báo lỗi cho người gửi
            WebSocketSession senderSession = userSessions.get(senderId);
            if (senderSession != null && senderSession.isOpen()) {
                sendError(senderSession, "Failed to send message: " + e.getMessage());
            }
        }
    }

    private void handleJoinConversation(WebSocketSession session, String userId, Map<String, Object> payload) {
        try {
            Long conversationId = Long.parseLong(payload.get("conversationId").toString());
            log.info("User {} joining conversation {}", userId, conversationId);

            chatService.markMessagesAsRead(conversationId, Long.parseLong(userId));

            sendToSession(session, Map.of(
                    "action", "JOINED_CONVERSATION",
                    "conversationId", conversationId
            ));
        } catch (Exception e) {
            log.error("Error joining conversation for user {}: {}", userId, e.getMessage(), e);
            sendError(session, "Error joining conversation: " + e.getMessage());
        }
    }

    // Utility methods
    private String getUserIdFromSession(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("userId=")) {
                    return param.split("=")[1];
                }
            }
        }
        return null;
    }

    private void sendToSession(WebSocketSession session, Object data) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(json));
                log.debug("Message sent successfully to session: {}", session.getId());
            } else {
                log.warn("Cannot send message: WebSocket session is closed");
            }
        } catch (Exception e) {
            log.error("Error sending message to WebSocket: {}", e.getMessage(), e);
        }
    }

    private void sendError(WebSocketSession session, String error) {
        sendToSession(session, Map.of(
                "action", "ERROR",
                "error", error
        ));
    }
}