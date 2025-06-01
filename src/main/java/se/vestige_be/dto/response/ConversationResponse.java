package se.vestige_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import se.vestige_be.pojo.Conversation;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private Long conversationId;
    private Long productId;
    private String productTitle;
    private Long sellerId;
    private String sellerUsername;
    private Long buyerId;
    private String buyerUsername;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private Integer unreadCount;

    public static ConversationResponse fromEntity(Conversation conversation, Long currentUserId) {
        return ConversationResponse.builder()
                .conversationId(conversation.getConversationId())
                .productId(conversation.getProduct() != null ? conversation.getProduct().getProductId() : null)
                .productTitle(conversation.getProduct() != null ? conversation.getProduct().getTitle() : null)
                .sellerId(conversation.getSeller().getUserId())
                .sellerUsername(conversation.getSeller().getUsername())
                .buyerId(conversation.getBuyer().getUserId())
                .buyerUsername(conversation.getBuyer().getUsername())
                .lastMessage(conversation.getMessages().isEmpty() ? null :
                        conversation.getMessages().get(conversation.getMessages().size() - 1).getContent())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .unreadCount(calculateUnreadCount(conversation, currentUserId))
                .build();
    }

    private static Integer calculateUnreadCount(Conversation conversation, Long currentUserId) {
        return (int) conversation.getMessages().stream()
                .filter(message -> !message.getSender().getUserId().equals(currentUserId))
                .filter(message -> !message.getIsRead())
                .count();
    }
}