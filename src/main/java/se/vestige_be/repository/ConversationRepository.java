package se.vestige_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.vestige_be.pojo.Conversation;
import se.vestige_be.pojo.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Find conversation between buyer and seller for a specific product
    Optional<Conversation> findByBuyerAndSellerAndProduct(User buyer, User seller, se.vestige_be.pojo.Product product);

    // Find all conversations where user is either buyer or seller
    List<Conversation> findByBuyerOrSellerOrderByLastMessageAtDesc(User buyer, User seller);

    // Find conversations by buyer
    List<Conversation> findByBuyerOrderByLastMessageAtDesc(User buyer);

    // Find conversations by seller
    List<Conversation> findBySellerOrderByLastMessageAtDesc(User seller);

    // Find active conversations (with recent activity)
    @Query("SELECT c FROM Conversation c WHERE " +
            "(c.buyer = :user OR c.seller = :user) AND " +
            "c.lastMessageAt >= :since")
    List<Conversation> findActiveConversationsByUser(
            @Param("user") User user,
            @Param("since") LocalDateTime since
    );

    // Count unread conversations for a user
    @Query("SELECT COUNT(DISTINCT c) FROM Conversation c " +
            "JOIN c.messages m " +
            "WHERE (c.buyer = :user OR c.seller = :user) " +
            "AND m.sender != :user " +
            "AND m.isRead = false")
    Long countUnreadConversations(@Param("user") User user);
}