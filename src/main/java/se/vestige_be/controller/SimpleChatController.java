package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.SimpleChatRecipientResponse;
import se.vestige_be.dto.response.SimpleMessageResponse;
import se.vestige_be.service.SimpleChatService;
import se.vestige_be.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat API", description = "API for handling chat functionalities")
@AllArgsConstructor
public class SimpleChatController {

    private final SimpleChatService chatService;
    private final UserService userService;

    /**
     * GET /api/chat/recipients - Get chat recipients by user ID
     */
    @GetMapping("/recipients")
    public ResponseEntity<List<SimpleChatRecipientResponse>> getChatRecipients(Authentication authentication) {
        String username = authentication.getName();
        Long userId = userService.findByUsername(username).getUserId();

        List<SimpleChatRecipientResponse> recipients = chatService.getChatRecipients(userId);
        return ResponseEntity.ok(recipients);
    }

    /**
     * GET /api/chat/history - Get chat history
     */
    @GetMapping("/history")
    public ResponseEntity<List<SimpleMessageResponse>> getChatHistory(
            @RequestParam Long recipientId,
            Authentication authentication) {

        String username = authentication.getName();
        Long userId = userService.findByUsername(username).getUserId();

        List<SimpleMessageResponse> history = chatService.getMessageHistory(userId, recipientId);
        return ResponseEntity.ok(history);
    }

    /**
     * DELETE /api/chat/clear - Clear chat history
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearChatHistory(Authentication authentication) {
        String username = authentication.getName();
        Long userId = userService.findByUsername(username).getUserId();

        chatService.clearUserHistory(userId);
        return ResponseEntity.ok().build();
    }
}