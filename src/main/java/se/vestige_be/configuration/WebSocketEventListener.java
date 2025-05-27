package se.vestige_be.configuration;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import se.vestige_be.dto.response.ChatMessageResponse;
import se.vestige_be.pojo.chat.ChatMessage;
import se.vestige_be.pojo.enums.MessageType;
import se.vestige_be.service.ChatService;

@Component
@AllArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatService chatService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        log.info("Received a new web socket connection");
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = (String) headerAccessor.getSessionAttributes().get("username");
        Long conversationId = (Long) headerAccessor.getSessionAttributes().get("conversationId");

        if (username != null && conversationId != null) {
            log.info("User {} disconnected from conversation {}", username, conversationId);

            try {
                // Create leave message
                ChatMessage leaveMessage = chatService.saveMessage(
                        conversationId,
                        username,
                        username + " left the conversation",
                        MessageType.LEAVE
                );

                // Send leave notification to conversation
                messagingTemplate.convertAndSend(
                        "/topic/conversation/" + conversationId,
                        ChatMessageResponse.fromEntity(leaveMessage)
                );
            } catch (Exception e) {
                log.error("Error handling user disconnect: {}", e.getMessage());
            }
        }
    }
}