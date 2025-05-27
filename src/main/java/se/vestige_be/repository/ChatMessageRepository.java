package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Conversation;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.chat.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    List<ChatMessage> findTop50ByConversationOrderByCreatedAtDesc(Conversation conversation);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversation = :conversation AND m.sender != :user AND m.isRead = false")
    Long countUnreadMessages(@Param("conversation") Conversation conversation, @Param("user") User user);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true, m.readAt = :readAt WHERE m.conversation = :conversation AND m.sender != :user AND m.isRead = false")
    void markMessagesAsRead(@Param("conversation") Conversation conversation, @Param("user") User user, @Param("readAt") LocalDateTime readAt);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation = :conversation AND m.content LIKE %:keyword%")
    List<ChatMessage> searchInConversation(@Param("conversation") Conversation conversation, @Param("keyword") String keyword);
}