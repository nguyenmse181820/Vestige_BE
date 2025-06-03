package se.vestige_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Conversation;
import se.vestige_be.pojo.Message;
import se.vestige_be.pojo.User;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Lấy tin nhắn trong cuộc trò chuyện với phân trang
    Page<Message> findByConversationOrderByCreatedAtDesc(Conversation conversation, Pageable pageable);

    // Lấy tin nhắn trong cuộc trò chuyện (không phân trang)
    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    // NEW: Lấy tin nhắn cuối cùng trong conversation
    List<Message> findTop1ByConversationOrderByCreatedAtDesc(Conversation conversation);

    // Đánh dấu tất cả tin nhắn trong cuộc trò chuyện là đã đọc
    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversation = :conversation AND m.sender != :user AND m.isRead = false")
    void markAllAsReadInConversation(@Param("conversation") Conversation conversation, @Param("user") User user);

    // Đếm tin nhắn chưa đọc trong cuộc trò chuyện
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation = :conversation AND m.sender != :user AND m.isRead = false")
    Long countUnreadInConversation(@Param("conversation") Conversation conversation, @Param("user") User user);
}