package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.ChatService;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "API for chat messages")
@AllArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> getConversationMessages(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status("success")
                        .message("Messages retrieved successfully")
                        .data(chatService.getConversationMessages(conversationId, username))
                        .build()
        );
    }

    @GetMapping("/conversations/{conversationId}/messages/recent")
    public ResponseEntity<?> getRecentMessages(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status("success")
                        .message("Recent messages retrieved successfully")
                        .data(chatService.getRecentMessages(conversationId, username))
                        .build()
        );
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<?> markMessagesAsRead(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        chatService.markMessagesAsRead(conversationId, username);

        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status("success")
                        .message("Messages marked as read")
                        .build()
        );
    }

    @GetMapping("/conversations/{conversationId}/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        Long unreadCount = chatService.getUnreadMessageCount(conversationId, username);

        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status("success")
                        .message("Unread count retrieved successfully")
                        .data(unreadCount)
                        .build()
        );
    }

    @GetMapping("/conversations/{conversationId}/search")
    public ResponseEntity<?> searchMessages(
            @PathVariable Long conversationId,
            @RequestParam String keyword,
            Authentication authentication
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                ObjectResponse.builder()
                        .status("success")
                        .message("Search results retrieved successfully")
                        .data(chatService.searchMessages(conversationId, keyword, username))
                        .build()
        );
    }
}