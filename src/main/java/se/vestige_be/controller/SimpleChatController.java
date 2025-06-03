package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.request.SimpleChatRequest;
import se.vestige_be.dto.response.SimpleChatRecipientResponse;
import se.vestige_be.dto.response.SimpleMessageResponse;
import se.vestige_be.dto.response.ObjectResponse;
import se.vestige_be.service.SimpleChatService;
import se.vestige_be.service.UserService;
import se.vestige_be.pojo.User;

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
     * GET /api/chat/users - Get all users available for chatting (excluding current user)
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsersForChat(Authentication authentication) {
        String username = authentication.getName();
        Long currentUserId = userService.findByUsername(username).getUserId();

        List<User> users = chatService.getAllUsersExcept(currentUserId);
        return ResponseEntity.ok(users);
    }

    /**
     * POST /api/chat/start - Start a new conversation with a user
     */
    @PostMapping("/start")
    public ResponseEntity<ObjectResponse> startConversation(
            @RequestParam Long recipientId,
            Authentication authentication) {

        String username = authentication.getName();
        Long userId = userService.findByUsername(username).getUserId();

        try {
            var conversation = chatService.getOrCreateConversation(userId, recipientId);
            return ResponseEntity.ok(ObjectResponse.builder()
                    .status("200")
                    .message("Conversation ready")
                    .data(conversation)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ObjectResponse.builder()
                    .status("400")
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * POST /api/chat/send - Send a message
     */
    @PostMapping("/send")
    public ResponseEntity<ObjectResponse> sendMessage(
            @RequestBody SimpleChatRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        Long userId = userService.findByUsername(username).getUserId();

        try {
            var message = chatService.sendMessage(userId, request.getRecipientId(), request.getContent());
            return ResponseEntity.ok(ObjectResponse.builder()
                    .status("200")
                    .message("Message sent successfully")
                    .data(message)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ObjectResponse.builder()
                    .status("400")
                    .message(e.getMessage())
                    .build());
        }
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