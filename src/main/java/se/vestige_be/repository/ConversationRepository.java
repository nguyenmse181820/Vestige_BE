package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Conversation;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.User;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Tìm cuộc trò chuyện giữa buyer và seller về một sản phẩm
    Optional<Conversation> findByProductAndBuyerAndSeller(Product product, User buyer, User seller);

    // Tìm tất cả cuộc trò chuyện của một user (có thể là buyer hoặc seller)
    @Query("SELECT c FROM Conversation c WHERE c.buyer = :user OR c.seller = :user ORDER BY c.lastMessageAt DESC")
    List<Conversation> findByUserOrderByLastMessageAtDesc(@Param("user") User user);

    // Tìm cuộc trò chuyện giữa 2 người dùng
    @Query("SELECT c FROM Conversation c WHERE (c.buyer = :user1 AND c.seller = :user2) OR (c.buyer = :user2 AND c.seller = :user1)")
    List<Conversation> findConversationsBetweenUsers(@Param("user1") User user1, @Param("user2") User user2);

    // Đếm số tin nhắn chưa đọc của user
    @Query("SELECT COUNT(m) FROM Message m JOIN m.conversation c WHERE (c.buyer = :user OR c.seller = :user) AND m.sender != :user AND m.isRead = false")
    Long countUnreadMessages(@Param("user") User user);
}