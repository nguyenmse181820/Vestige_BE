package se.vestige_be.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.enums.ProductStatus;
import se.vestige_be.pojo.enums.TransactionStatus;
import se.vestige_be.pojo.enums.OrderItemStatus;
import se.vestige_be.pojo.enums.EscrowStatus;
import se.vestige_be.repository.ProductRepository;
import se.vestige_be.repository.TransactionRepository;
import se.vestige_be.service.OrderService;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCleanupTask {

    private final ProductRepository productRepository;
    private final TransactionRepository transactionRepository;
    private final OrderService orderService;

    @Scheduled(fixedRate = 15 * 60 * 1000)
    @Transactional
    public void cleanupAbandonedPayments() {
        log.info("Starting cleanup of abandoned PayOS payments...");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(15);
            
            List<Product> abandonedProducts = productRepository.findByStatusAndUpdatedAtBefore(
                    ProductStatus.PENDING_PAYMENT, cutoffTime);
            
            if (abandonedProducts.isEmpty()) {
                log.info("No abandoned payments found to clean up");
                return;
            }
            
            log.info("Found {} products with abandoned payments to clean up", abandonedProducts.size());
            
            int cleanedUp = 0;
            int alreadyPaid = 0;
            
            for (Product product : abandonedProducts) {
                try {
                    // Check if there's a PAID transaction for this product
                    List<Transaction> transactions = transactionRepository.findByOrderItemProductProductIdAndStatus(
                            product.getProductId(), TransactionStatus.PAID);
                    
                    if (!transactions.isEmpty()) {
                        // Payment was completed, product should be SOLD
                        product.setStatus(ProductStatus.SOLD);
                        product.setSoldAt(LocalDateTime.now());
                        productRepository.save(product);
                        alreadyPaid++;
                        
                        log.info("Product {} was actually paid - updated to SOLD", product.getProductId());
                        continue;
                    }
                    
                    // Check if there's a pending transaction for this product
                    List<Transaction> pendingTransactions = transactionRepository.findByOrderItemProductProductIdAndStatus(
                            product.getProductId(), TransactionStatus.PENDING);
                    
                    if (!pendingTransactions.isEmpty()) {
                        // Cancel the pending transactions and restore product status
                        for (Transaction transaction : pendingTransactions) {
                            if (transaction.getOrderItem() != null) {
                                try {
                                    // Cancel the transaction
                                    transaction.setStatus(TransactionStatus.CANCELLED);
                                    transactionRepository.save(transaction);
                                    
                                    // Cancel the order item
                                    OrderItem orderItem = transaction.getOrderItem();
                                    orderItem.setStatus(OrderItemStatus.CANCELLED);
                                    orderItem.setEscrowStatus(EscrowStatus.REFUNDED);
                                    
                                    // This will be handled by the product restoration below
                                    
                                    log.info("Cancelled abandoned transaction {} for product {}", 
                                            transaction.getTransactionId(), product.getProductId());
                                } catch (Exception e) {
                                    log.error("Failed to cancel transaction {} for product {}: {}", 
                                            transaction.getTransactionId(), 
                                            product.getProductId(), e.getMessage());
                                }
                            }
                        }
                        
                        // Restore product status
                        product.setStatus(ProductStatus.ACTIVE);
                        product.setSoldAt(null);
                        productRepository.save(product);
                        
                        cleanedUp++;
                    } else {
                        // No transaction found, just restore product status
                        product.setStatus(ProductStatus.ACTIVE);
                        product.setSoldAt(null);
                        productRepository.save(product);
                        cleanedUp++;
                        
                        log.info("Restored product {} to ACTIVE (no transaction found)", product.getProductId());
                    }
                    
                } catch (Exception e) {
                    log.error("Error cleaning up abandoned payment for product {}: {}", 
                            product.getProductId(), e.getMessage(), e);
                }
            }
            
            log.info("Payment cleanup completed: {} cleaned up, {} already paid", cleanedUp, alreadyPaid);
            
        } catch (Exception e) {
            log.error("Error during payment cleanup task: {}", e.getMessage(), e);
        }
    }
}
