package se.vestige_be.pojo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @ToString.Exclude
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @ToString.Exclude
    private User sender;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void markAsRead() {
        this.isRead = true;
    }

    public boolean isFromUser(User user) {
        return this.sender != null && this.sender.getUserId().equals(user.getUserId());
    }

    public boolean isToUser(User user) {
        if (conversation == null || conversation.getBuyer() == null || conversation.getSeller() == null) {
            return false;
        }
        Long userId = user.getUserId();
        return !isFromUser(user) &&
                (userId.equals(conversation.getBuyer().getUserId()) ||
                        userId.equals(conversation.getSeller().getUserId()));
    }
}