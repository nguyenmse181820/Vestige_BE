package se.vestige_be.controller;

import lombok.AllArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;
import se.vestige_be.dto.request.ChatMessageRequest;
import se.vestige_be.dto.response.ChatMessageResponse;
import se.vestige_be.pojo.chat.ChatMessage;
import se.vestige_be.pojo.enums.MessageType;
import se.vestige_be.service.ChatService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
@AllArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessageSendingOperations messagingTemplate;

    // Send message to a specific conversation
    @MessageMapping("/chat.send/{conversationId}")
    public void sendMessage(
            @DestinationVariable Long conversationId,
            @Payload ChatMessageRequest chatMessage,
            Principal principal
    ) {
        // Get sender username from JWT token
        String senderUsername = principal.getName();

        // Save message to database
        ChatMessage savedMessage = chatService.saveMessage(
                conversationId,
                senderUsername,
                chatMessage.getContent(),
                MessageType.CHAT
        );

        // Convert to response DTO
        ChatMessageResponse response = ChatMessageResponse.fromEntity(savedMessage);

        // Send to conversation topic (both participants receive)
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                response
        );
    }

    // User joins a conversation
    @MessageMapping("/chat.join/{conversationId}")
    public void joinConversation(
            @DestinationVariable Long conversationId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        String username = principal.getName();

        // Store username in WebSocket session
        headerAccessor.getSessionAttributes().put("username", username);
        headerAccessor.getSessionAttributes().put("conversationId", conversationId);

        // Create join message
        ChatMessage joinMessage = chatService.saveMessage(
                conversationId,
                username,
                username + " joined the conversation",
                MessageType.JOIN
        );

        // Send join notification
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId,
                ChatMessageResponse.fromEntity(joinMessage)
        );
    }

    // Mark messages as read
    @MessageMapping("/chat.markRead/{conversationId}")
    public void markAsRead(
            @DestinationVariable Long conversationId,
            Principal principal
    ) {
        String username = principal.getName();

        // Mark messages as read in database
        chatService.markMessagesAsRead(conversationId, username);

        // Notify other participant that messages were read
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId + "/read",
                Map.of(
                        "readBy", username,
                        "readAt", LocalDateTime.now()
                )
        );
    }
}