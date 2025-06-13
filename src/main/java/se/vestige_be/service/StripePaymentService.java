package se.vestige_be.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.OrderCreateRequest;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.Product;
import se.vestige_be.pojo.enums.*;
import se.vestige_be.repository.*;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
public class StripePaymentService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final OfferRepository offerRepository;
    private final FeeTierService feeTierService;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public StripePaymentService(OrderRepository orderRepository,
                                TransactionRepository transactionRepository,
                                ProductRepository productRepository,
                                UserRepository userRepository,
                                UserAddressRepository userAddressRepository,
                                OfferRepository offerRepository,
                                FeeTierService feeTierService) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.offerRepository = offerRepository;
        this.feeTierService = feeTierService;
    }

    /**
     * Create payment for single product order
     */
    @Transactional
    public PaymentResponse createPayment(Long buyerId, OrderCreateRequest request) {
        try {
            // Validate single product
            if (request.getItems().size() != 1) {
                throw new BusinessLogicException("Only single product orders are supported");
            }

            OrderCreateRequest.OrderItemRequest itemRequest = request.getItems().get(0);

            // Load and validate entities
            User buyer = userRepository.findById(buyerId)
                    .orElseThrow(() -> new BusinessLogicException("Buyer not found"));

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessLogicException("Product not found"));

            UserAddress shippingAddress = userAddressRepository.findById(request.getShippingAddressId())
                    .orElseThrow(() -> new BusinessLogicException("Shipping address not found"));

            validatePurchase(buyer, product, shippingAddress);

            // Calculate price and fees
            BigDecimal itemPrice = calculateItemPrice(itemRequest, product, buyerId);
            BigDecimal platformFee = feeTierService.calculatePlatformFee(itemPrice, product.getSeller());
            BigDecimal feePercentage = feeTierService.calculateFeePercentage(itemPrice, product.getSeller());

            // Create Order (simplified structure)
            Order order = Order.builder()
                    .buyer(buyer)
                    .totalAmount(itemPrice)
                    .shippingAddress(shippingAddress)
                    .paymentMethod(request.getPaymentMethod())
                    .status(OrderStatus.PENDING)
                    .build();
            order = orderRepository.save(order);

            // Create OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .seller(product.getSeller())
                    .price(itemPrice)
                    .platformFee(platformFee)
                    .feePercentage(feePercentage)
                    .status(OrderItemStatus.PENDING)
                    .escrowStatus(EscrowStatus.HOLDING)
                    .build();

            // Create Transaction
            Transaction transaction = Transaction.builder()
                    .orderItem(orderItem)
                    .seller(product.getSeller())
                    .buyer(buyer)
                    .offer(itemRequest.getOfferId() != null ?
                            offerRepository.findById(itemRequest.getOfferId()).orElse(null) : null)
                    .amount(itemPrice)
                    .platformFee(platformFee)
                    .feePercentage(feePercentage)
                    .shippingAddress(shippingAddress)
                    .status(TransactionStatus.PENDING)
                    .escrowStatus(EscrowStatus.HOLDING)
                    .buyerProtectionEligible(true)
                    .build();
            transaction = transactionRepository.save(transaction);

            // Create Stripe checkout session
            String checkoutUrl = createStripeCheckoutSession(order, transaction, product);

            // Mark product as pending payment
            product.setStatus(ProductStatus.PENDING_PAYMENT);
            productRepository.save(product);

            return PaymentResponse.builder()
                    .orderId(order.getOrderId())
                    .transactionId(transaction.getTransactionId())
                    .productTitle(product.getTitle())
                    .amount(itemPrice)
                    .platformFee(platformFee)
                    .sellerAmount(itemPrice.subtract(platformFee))
                    .checkoutUrl(checkoutUrl)
                    .status("PENDING_PAYMENT")
                    .build();

        } catch (Exception e) {
            log.error("Error creating payment: {}", e.getMessage(), e);
            throw new BusinessLogicException("Failed to create payment: " + e.getMessage());
        }
    }

    private void validatePurchase(User buyer, Product product, UserAddress shippingAddress) {
        if (!ProductStatus.ACTIVE.equals(product.getStatus())) {
            throw new BusinessLogicException("Product is not available for purchase");
        }

        if (product.getSeller().getUserId().equals(buyer.getUserId())) {
            throw new BusinessLogicException("Cannot purchase your own product");
        }

        if (!shippingAddress.getUser().getUserId().equals(buyer.getUserId())) {
            throw new BusinessLogicException("Shipping address does not belong to buyer");
        }

        if (!"active".equals(buyer.getAccountStatus())) {
            throw new BusinessLogicException("Buyer account is not active");
        }

        if (!"active".equals(product.getSeller().getAccountStatus())) {
            throw new BusinessLogicException("Seller account is not active");
        }
    }

    private BigDecimal calculateItemPrice(OrderCreateRequest.OrderItemRequest itemRequest, Product product, Long buyerId) {
        if (itemRequest.getOfferId() != null) {
            Offer offer = offerRepository.findById(itemRequest.getOfferId())
                    .orElseThrow(() -> new BusinessLogicException("Offer not found"));

            if (!offer.getProduct().getProductId().equals(product.getProductId())) {
                throw new BusinessLogicException("Offer does not match product");
            }
            if (!OfferStatus.ACCEPTED.equals(offer.getStatus())) {
                throw new BusinessLogicException("Offer is not accepted");
            }
            if (!offer.getBuyer().getUserId().equals(buyerId)) {
                throw new BusinessLogicException("Offer does not belong to buyer");
            }

            return offer.getAmount();
        }
        return product.getPrice();
    }

    private String createStripeCheckoutSession(Order order, Transaction transaction, Product product) throws StripeException {
        // Ensure seller has Stripe account
        String sellerStripeAccountId = getOrCreateSellerStripeAccount(order.getBuyer()); // Note: Using buyer for now, should be seller

        // Convert VND to cents
        long amountInCents = order.getTotalAmount().multiply(new BigDecimal("100")).longValue();
        long platformFeeInCents = transaction.getPlatformFee().multiply(new BigDecimal("100")).longValue();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/order/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/product/" + product.getProductId())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("vnd")
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(product.getTitle())
                                        .setDescription("Condition: " + product.getCondition())
                                        .addImage(getProductImageUrl(product))
                                        .build())
                                .setUnitAmount(amountInCents)
                                .build())
                        .setQuantity(1L)
                        .build())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setApplicationFeeAmount(platformFeeInCents)
                        .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                                .setDestination(sellerStripeAccountId)
                                .build())
                        .putMetadata("order_id", order.getOrderId().toString())
                        .putMetadata("transaction_id", transaction.getTransactionId().toString())
                        .putMetadata("seller_id", transaction.getSeller().getUserId().toString())
                        .putMetadata("buyer_id", transaction.getBuyer().getUserId().toString())
                        .putMetadata("product_id", product.getProductId().toString())
                        .build())
                .build();

        Session session = Session.create(params);

        log.info("Created Stripe checkout session: {} for transaction: {}", session.getId(), transaction.getTransactionId());
        return session.getUrl();
    }

    private String getOrCreateSellerStripeAccount(User seller) throws StripeException {
        // For now, return a placeholder - you'll need to implement Stripe Connect properly
        // This assumes you have stripe account fields in User entity
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry("VN")
                .setEmail(seller.getEmail())
                .setCapabilities(AccountCreateParams.Capabilities.builder()
                        .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                .setRequested(true)
                                .build())
                        .build())
                .setBusinessProfile(AccountCreateParams.BusinessProfile.builder()
                        .setUrl(frontendUrl + "/seller/" + seller.getUserId())
                        .build())
                .build();

        Account account = Account.create(params);

        log.info("Created Stripe account for seller {}: {}", seller.getUsername(), account.getId());
        return account.getId();
    }

    private String getProductImageUrl(Product product) {
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            return product.getImages().stream()
                    .filter(ProductImage::getIsPrimary)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(product.getImages().get(0).getImageUrl());
        }
        return null;
    }

    /**
     * Handle successful payment webhook
     */
    @Transactional
    public void handlePaymentSuccess(PaymentIntent paymentIntent) {
        try {
            String transactionIdStr = paymentIntent.getMetadata().get("transaction_id");
            if (transactionIdStr == null) {
                log.error("Missing transaction_id in payment intent: {}", paymentIntent.getId());
                return;
            }

            Long transactionId = Long.parseLong(transactionIdStr);
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new BusinessLogicException("Transaction not found"));

            // Update transaction
            transaction.setStatus(TransactionStatus.PAID);
            transaction.setPaidAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            // Update order
            Order order = transaction.getOrderItem().getOrder();
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderRepository.save(order);

            // Update order item
            OrderItem orderItem = transaction.getOrderItem();
            orderItem.setStatus(OrderItemStatus.PROCESSING);

            // Update product status
            Product product = orderItem.getProduct();
            product.setStatus(ProductStatus.SOLD);
            product.setSoldAt(LocalDateTime.now());
            productRepository.save(product);

            log.info("Payment processed successfully for transaction: {}", transactionId);

        } catch (Exception e) {
            log.error("Error processing payment success: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle payment failure
     */
    @Transactional
    public void handlePaymentFailed(PaymentIntent paymentIntent) {
        try {
            String transactionIdStr = paymentIntent.getMetadata().get("transaction_id");
            if (transactionIdStr == null) {
                return;
            }

            Long transactionId = Long.parseLong(transactionIdStr);
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new BusinessLogicException("Transaction not found"));

            // Update transaction
            transaction.setStatus(TransactionStatus.CANCELLED);
            transactionRepository.save(transaction);

            // Update order
            Order order = transaction.getOrderItem().getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            // Update order item
            OrderItem orderItem = transaction.getOrderItem();
            orderItem.setStatus(OrderItemStatus.CANCELLED);

            // Reset product status
            Product product = orderItem.getProduct();
            product.setStatus(ProductStatus.ACTIVE);
            productRepository.save(product);

            log.info("Payment failed processed for transaction: {}", transactionId);

        } catch (Exception e) {
            log.error("Error processing payment failure: {}", e.getMessage(), e);
        }
    }

    /**
     * Get transaction status
     */
    public TransactionStatusResponse getTransactionStatus(Long transactionId, Long userId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessLogicException("Transaction not found"));

        // Check access permission
        boolean isBuyer = transaction.getBuyer().getUserId().equals(userId);
        boolean isSeller = transaction.getSeller().getUserId().equals(userId);

        if (!isBuyer && !isSeller) {
            throw new BusinessLogicException("Access denied to this transaction");
        }

        // Build response
        Order order = transaction.getOrderItem().getOrder();
        Product product = transaction.getOrderItem().getProduct();

        ProductInfo productInfo = new ProductInfo(
                product.getProductId(),
                product.getTitle(),
                getProductImageUrl(product),
                product.getCondition().toString()
        );

        UserInfo sellerInfo = new UserInfo(
                transaction.getSeller().getUserId(),
                transaction.getSeller().getUsername(),
                transaction.getSeller().getFirstName(),
                transaction.getSeller().getLastName()
        );

        UserInfo buyerInfo = new UserInfo(
                transaction.getBuyer().getUserId(),
                transaction.getBuyer().getUsername(),
                transaction.getBuyer().getFirstName(),
                transaction.getBuyer().getLastName()
        );

        return new TransactionStatusResponse(
                transaction.getTransactionId(),
                order.getOrderId(),
                transaction.getStatus().toString(),
                transaction.getEscrowStatus().toString(),
                transaction.getAmount(),
                transaction.getPlatformFee(),
                transaction.getCreatedAt(),
                transaction.getPaidAt(),
                transaction.getDeliveredAt(),
                productInfo,
                sellerInfo,
                buyerInfo
        );
    }

    // Response DTOs
    public static class PaymentResponse {
        private Long orderId;
        private Long transactionId;
        private String productTitle;
        private BigDecimal amount;
        private BigDecimal platformFee;
        private BigDecimal sellerAmount;
        private String checkoutUrl;
        private String status;

        public static PaymentResponseBuilder builder() {
            return new PaymentResponseBuilder();
        }

        public static class PaymentResponseBuilder {
            private PaymentResponse response = new PaymentResponse();

            public PaymentResponseBuilder orderId(Long orderId) {
                response.orderId = orderId;
                return this;
            }

            public PaymentResponseBuilder transactionId(Long transactionId) {
                response.transactionId = transactionId;
                return this;
            }

            public PaymentResponseBuilder productTitle(String productTitle) {
                response.productTitle = productTitle;
                return this;
            }

            public PaymentResponseBuilder amount(BigDecimal amount) {
                response.amount = amount;
                return this;
            }

            public PaymentResponseBuilder platformFee(BigDecimal platformFee) {
                response.platformFee = platformFee;
                return this;
            }

            public PaymentResponseBuilder sellerAmount(BigDecimal sellerAmount) {
                response.sellerAmount = sellerAmount;
                return this;
            }

            public PaymentResponseBuilder checkoutUrl(String checkoutUrl) {
                response.checkoutUrl = checkoutUrl;
                return this;
            }

            public PaymentResponseBuilder status(String status) {
                response.status = status;
                return this;
            }

            public PaymentResponse build() {
                return response;
            }
        }

        // Getters
        public Long getOrderId() { return orderId; }
        public Long getTransactionId() { return transactionId; }
        public String getProductTitle() { return productTitle; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getPlatformFee() { return platformFee; }
        public BigDecimal getSellerAmount() { return sellerAmount; }
        public String getCheckoutUrl() { return checkoutUrl; }
        public String getStatus() { return status; }
    }

    public static class TransactionStatusResponse {
        private Long transactionId;
        private Long orderId;
        private String status;
        private String escrowStatus;
        private BigDecimal amount;
        private BigDecimal platformFee;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
        private LocalDateTime deliveredAt;
        private ProductInfo product;
        private UserInfo seller;
        private UserInfo buyer;

        public TransactionStatusResponse(Long transactionId, Long orderId, String status, String escrowStatus,
                                         BigDecimal amount, BigDecimal platformFee,
                                         LocalDateTime createdAt, LocalDateTime paidAt,
                                         LocalDateTime deliveredAt, ProductInfo product,
                                         UserInfo seller, UserInfo buyer) {
            this.transactionId = transactionId;
            this.orderId = orderId;
            this.status = status;
            this.escrowStatus = escrowStatus;
            this.amount = amount;
            this.platformFee = platformFee;
            this.createdAt = createdAt;
            this.paidAt = paidAt;
            this.deliveredAt = deliveredAt;
            this.product = product;
            this.seller = seller;
            this.buyer = buyer;
        }

        // Getters
        public Long getTransactionId() { return transactionId; }
        public Long getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public String getEscrowStatus() { return escrowStatus; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getPlatformFee() { return platformFee; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public LocalDateTime getDeliveredAt() { return deliveredAt; }
        public ProductInfo getProduct() { return product; }
        public UserInfo getSeller() { return seller; }
        public UserInfo getBuyer() { return buyer; }
    }

    public static class ProductInfo {
        private Long productId;
        private String title;
        private String imageUrl;
        private String condition;

        public ProductInfo(Long productId, String title, String imageUrl, String condition) {
            this.productId = productId;
            this.title = title;
            this.imageUrl = imageUrl;
            this.condition = condition;
        }

        // Getters
        public Long getProductId() { return productId; }
        public String getTitle() { return title; }
        public String getImageUrl() { return imageUrl; }
        public String getCondition() { return condition; }
    }

    public static class UserInfo {
        private Long userId;
        private String username;
        private String firstName;
        private String lastName;

        public UserInfo(Long userId, String username, String firstName, String lastName) {
            this.userId = userId;
            this.username = username;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        // Getters
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
    }
}