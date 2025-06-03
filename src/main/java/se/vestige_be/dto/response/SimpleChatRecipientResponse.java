package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleChatRecipientResponse {
    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private Long conversationId;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Integer unreadCount;
}
