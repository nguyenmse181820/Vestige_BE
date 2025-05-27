package se.vestige_be.dto.request;

import lombok.*;
import se.vestige_be.pojo.enums.MessageType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequest {
    private Long conversationId;
    private String content;
    private MessageType type;
}