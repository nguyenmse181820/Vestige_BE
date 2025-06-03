package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import se.vestige_be.pojo.Message;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class SimpleMessageResponse {
    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static SimpleMessageResponse fromEntity(Message message) {
        try {
            log.info("Converting message to response: messageId={}, content={}, senderId={}",
                    message.getMessageId(), message.getContent(), message.getSender().getUserId());

            SimpleMessageResponse response = SimpleMessageResponse.builder()
                    .messageId(message.getMessageId())
                    .conversationId(message.getConversation().getConversationId())
                    .senderId(message.getSender().getUserId())
                    .senderUsername(message.getSender().getUsername())
                    .content(message.getContent())
                    .isRead(message.getIsRead() != null ? message.getIsRead() : false)
                    .createdAt(message.getCreatedAt())
                    .build();

            log.info("Message response created: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error converting message to response: {}", e.getMessage(), e);
            // Return minimal response to prevent total failure
            return SimpleMessageResponse.builder()
                    .messageId(message.getMessageId())
                    .content(message.getContent())
                    .senderId(message.getSender().getUserId())
                    .senderUsername(message.getSender().getUsername())
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
        }
    }
}