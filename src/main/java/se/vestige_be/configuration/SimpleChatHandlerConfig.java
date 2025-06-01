package se.vestige_be.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import se.vestige_be.service.SimpleChatService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@AllArgsConstructor
public class SimpleChatHandlerConfig implements WebSocketHandler {

    private final SimpleChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Lưu sessions theo userId
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());

        // Lấy userId từ query parameter
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("User {} connected with session {}", userId, session.getId());

            // Gửi confirmation
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

            switch (action) {
                case "GET_RECIPIENTS":
                    handleGetRecipients(session, userId);
                    break;
                case "GET_HISTORY":
                    handleGetHistory(session, userId, payload);
                    break;
                case "SEND_MESSAGE":
                    handleSendMessage(userId, payload);
                    break;
                case "JOIN_CONVERSATION":
                    handleJoinConversation(session, userId, payload);
                    break;
                default:
                    sendError(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Error handling message from user {}: {}", userId, e.getMessage());
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
            var recipients = chatService.getChatRecipients(Long.parseLong(userId));
            sendToSession(session, Map.of(
                    "action", "RECIPIENTS_RESPONSE",
                    "recipients", recipients
            ));
        } catch (Exception e) {
            sendError(session, "Error getting recipients: " + e.getMessage());
        }
    }

    private void handleGetHistory(WebSocketSession session, String userId, Map<String, Object> payload) {
        try {
            Long recipientId = Long.parseLong(payload.get("recipientId").toString());
            var history = chatService.getMessageHistory(Long.parseLong(userId), recipientId);

            sendToSession(session, Map.of(
                    "action", "HISTORY_RESPONSE",
                    "recipientId", recipientId,
                    "messages", history
            ));
        } catch (Exception e) {
            sendError(session, "Error getting history: " + e.getMessage());
        }
    }

    private void handleSendMessage(String senderId, Map<String, Object> payload) {
        try {
            Long senderIdLong = Long.parseLong(senderId);
            Long recipientId = Long.parseLong(payload.get("recipientId").toString());
            String content = payload.get("content").toString();

            // Lưu message vào DB và lấy response
            var messageResponse = chatService.sendMessage(senderIdLong, recipientId, content);

            // Gửi cho người nhận
            WebSocketSession recipientSession = userSessions.get(recipientId.toString());
            if (recipientSession != null && recipientSession.isOpen()) {
                sendToSession(recipientSession, Map.of(
                        "action", "NEW_MESSAGE",
                        "message", messageResponse
                ));
            }

            // Confirm cho người gửi
            WebSocketSession senderSession = userSessions.get(senderId);
            if (senderSession != null && senderSession.isOpen()) {
                sendToSession(senderSession, Map.of(
                        "action", "MESSAGE_SENT",
                        "message", messageResponse
                ));
            }

        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    private void handleJoinConversation(WebSocketSession session, String userId, Map<String, Object> payload) {
        try {
            Long conversationId = Long.parseLong(payload.get("conversationId").toString());
            chatService.markMessagesAsRead(conversationId, Long.parseLong(userId));

            sendToSession(session, Map.of(
                    "action", "JOINED_CONVERSATION",
                    "conversationId", conversationId
            ));
        } catch (Exception e) {
            sendError(session, "Error joining conversation: " + e.getMessage());
        }
    }

    // Utility methods
    private String getUserIdFromSession(WebSocketSession session) {
        // Lấy từ query parameter userId
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
            }
        } catch (Exception e) {
            log.error("Error sending message to session: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String error) {
        sendToSession(session, Map.of(
                "action", "ERROR",
                "error", error
        ));
    }
}