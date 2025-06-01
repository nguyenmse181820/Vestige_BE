package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.Message;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleMessageResponse {
    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static SimpleMessageResponse fromEntity(Message message) {
        return SimpleMessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversation().getConversationId())
                .senderId(message.getSender().getUserId())
                .senderUsername(message.getSender().getUsername())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}