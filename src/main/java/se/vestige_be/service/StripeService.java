package se.vestige_be.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.OrderItem;
import se.vestige_be.pojo.Transaction;
import se.vestige_be.pojo.User;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class StripeService {

    @Value("${stripe.api.secret-key}")
    private String secretKey;

    @Value("${stripe.currency:vnd}")
    private String currency;

    private final OrderService orderService;
    private final UserService userService;
    
    // Simple in-memory storage for processed webhook events
    // In production, this should be stored in database
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public StripeService(@Lazy OrderService orderService, @Lazy UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe initialized with currency: {}", currency);
    }    /**
     * Creates a Stripe Express account for seller onboarding - VIETNAM COMPATIBLE
     * 
     * Note: Vietnam accounts only support 'transfers' capability (not card_payments).
     * This means sellers can receive payouts but cannot directly charge customers.
     * Payment processing happens through the platform account with cross-border transfers.
     */
    public Account createExpressAccount(User user) throws StripeException {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("VN") // Vietnam - supports transfers only
                    .setEmail(user.getEmail())
                    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)                    .setBusinessProfile(
                            AccountCreateParams.BusinessProfile.builder()
                                    .setName(user.getFirstName() + " " + user.getLastName())
                                    .setMcc("5734") // Merchant Category Code for second-hand goods
                                    .setProductDescription("Vestige Marketplace - Second-hand goods")
                                    .setUrl("https://vestige-marketplace.com") // Add your marketplace URL
                                    .build()
                    )
                    .setCapabilities(buildCapabilitiesForCountry("VN"))
                    .setTosAcceptance(
                            AccountCreateParams.TosAcceptance.builder()
                                    .setServiceAgreement(getServiceAgreementForCountry("VN"))
                                    .build()
                    )
                    .putMetadata("user_id", user.getUserId().toString())
                    .putMetadata("username", user.getUsername())
                    .putMetadata("platform", "vestige")
                    .build();

            Account account = Account.create(params);
            log.info("Created Stripe Express account {} for user {}", account.getId(), user.getUsername());
            return account;
        } catch (StripeException e) {
            log.error("Failed to create Stripe account for user {}: {}", user.getUsername(), e.getMessage());
            throw e;
        }
    }

    /**
     * Creates an account link for seller onboarding completion
     */
    public AccountLink createAccountLink(String accountId, String refreshUrl, String returnUrl) throws StripeException {
        try {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build();

            AccountLink link = AccountLink.create(params);
            log.info("Created onboarding link for account {}", accountId);
            return link;
        } catch (StripeException e) {
            log.error("Failed to create account link for {}: {}", accountId, e.getMessage());
            throw e;
        }
    }

    /**
     * Checks if seller account is fully set up and can receive transfers
     */
    public boolean isAccountSetupComplete(String accountId) throws StripeException {
        try {
            Account account = Account.retrieve(accountId);
            boolean canReceive = account.getChargesEnabled() && account.getPayoutsEnabled();
            log.debug("Account {} setup status: charges={}, payouts={}",
                    accountId, account.getChargesEnabled(), account.getPayoutsEnabled());
            return canReceive;
        } catch (StripeException e) {
            log.error("Failed to check account status for {}: {}", accountId, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a PaymentIntent to charge buyer - funds held in platform account for escrow
     */
    public PaymentIntent createPlatformCharge(BigDecimal totalAmount, Long orderId) throws StripeException {
        try {
            long amountInVND = totalAmount.longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInVND)
                    .setCurrency(currency)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("order_id", orderId.toString())
                    .putMetadata("platform", "vestige")
                    .putMetadata("type", "escrow_charge")
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            log.info("Created PaymentIntent {} for order {} amount {} VND",
                    paymentIntent.getId(), orderId, amountInVND);
            return paymentIntent; // <-- Trả về cả đối tượng PaymentIntent
        } catch (StripeException e) {
            log.error("Failed to create payment intent for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    /**
     * Enhanced payment verification with amount and order validation
     */    public boolean verifyPaymentForOrder(String paymentIntentId, BigDecimal expectedAmount, Long orderId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            
            // Check payment status with detailed logging
            String status = paymentIntent.getStatus();
            log.info("PaymentIntent {} status: {}", paymentIntentId, status);
            
            if (!"succeeded".equals(status)) {
                // Provide specific error messages based on status
                switch (status) {
                    case "requires_payment_method":
                        log.warn("Payment {} requires payment method - frontend needs to confirm payment with Stripe Elements", paymentIntentId);
                        break;
                    case "requires_confirmation":
                        log.warn("Payment {} requires confirmation - frontend needs to confirm payment", paymentIntentId);
                        break;
                    case "requires_action":
                        log.warn("Payment {} requires additional action (3D Secure, etc.)", paymentIntentId);
                        break;
                    case "processing":
                        log.warn("Payment {} is still processing", paymentIntentId);
                        break;
                    case "canceled":
                        log.warn("Payment {} was canceled", paymentIntentId);
                        break;
                    case "requires_capture":
                        log.warn("Payment {} requires manual capture", paymentIntentId);
                        break;
                    default:
                        log.warn("Payment {} has unexpected status: {}", paymentIntentId, status);
                }
                return false;
            }
            
            // Validate amount matches (convert to minor currency unit)
            long expectedAmountMinor = expectedAmount.longValue();
            if (!paymentIntent.getAmount().equals(expectedAmountMinor)) {
                log.error("Amount mismatch for PI {}: expected={}, actual={}", 
                    paymentIntentId, expectedAmountMinor, paymentIntent.getAmount());
                return false;
            }
            
            // Validate order metadata
            String orderIdMetadata = paymentIntent.getMetadata().get("order_id");
            if (orderIdMetadata == null || !orderId.toString().equals(orderIdMetadata)) {
                log.error("Order ID mismatch for PI {}: expected={}, metadata={}", 
                    paymentIntentId, orderId, orderIdMetadata);
                return false;
            }
            
            log.info("Payment verification successful for PI {} order {} amount {}", 
                paymentIntentId, orderId, expectedAmount);
            return true;
            
        } catch (StripeException e) {
            log.error("Failed to verify payment {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }    /**
     * Verifies that a payment was successful - DEPRECATED, use verifyPaymentForOrder instead
     */
    @Deprecated
    public boolean verifyPayment(String paymentIntentId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            String status = paymentIntent.getStatus();
            boolean succeeded = "succeeded".equals(status);
            
            log.info("Payment verification for {}: {} (Status: {})", 
                paymentIntentId, succeeded ? "SUCCESS" : "FAILED", status);
            
            return succeeded;
        } catch (StripeException e) {
            log.error("Failed to verify payment {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Releases payment to seller after escrow period (7 days)
     * Platform fee is automatically retained
     */
    public Transfer releasePaymentToSeller(OrderItem orderItem, Transaction transaction) throws StripeException {
        try {
            // Calculate seller amount (total - platform fee)
            BigDecimal sellerAmount = orderItem.getPrice().subtract(orderItem.getPlatformFee());
            long amountInVND = sellerAmount.longValue();

            // Get the charge from the PaymentIntent
            PaymentIntent paymentIntent = PaymentIntent.retrieve(transaction.getStripePaymentIntentId());
            String chargeId = paymentIntent.getLatestCharge();

            if (chargeId == null) {
                throw new BusinessLogicException("No charge found for PaymentIntent: " + transaction.getStripePaymentIntentId());
            }

            // Create transfer to seller
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(amountInVND)
                    .setCurrency(currency)
                    .setDestination(orderItem.getSeller().getStripeAccountId())
                    .setSourceTransaction(chargeId)
                    .putMetadata("order_id", orderItem.getOrder().getOrderId().toString())
                    .putMetadata("order_item_id", orderItem.getOrderItemId().toString())
                    .putMetadata("transaction_id", transaction.getTransactionId().toString())
                    .putMetadata("seller_username", orderItem.getSeller().getUsername())
                    .putMetadata("platform_fee", orderItem.getPlatformFee().toString())
                    .build();

            Transfer transfer = Transfer.create(params);
            log.info("Released {} VND to seller {} (Platform fee: {} VND) - Transfer: {}",
                    amountInVND, orderItem.getSeller().getUsername(),
                    orderItem.getPlatformFee(), transfer.getId());
            return transfer;
        } catch (StripeException e) {
            log.error("Failed to release payment for transaction {}: {}", transaction.getTransactionId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Processes refund for cancelled orders
     */
    public Refund refundPayment(String paymentIntentId, BigDecimal refundAmount) throws StripeException {
        try {
            long amountInVND = refundAmount.longValue();

            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            String chargeId = paymentIntent.getLatestCharge();

            if (chargeId == null) {
                throw new BusinessLogicException("No charge found for PaymentIntent: " + paymentIntentId);
            }

            RefundCreateParams params = RefundCreateParams.builder()
                    .setCharge(chargeId)
                    .setAmount(amountInVND)
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .putMetadata("refund_type", "order_cancellation")
                    .putMetadata("platform", "vestige")
                    .build();

            Refund refund = Refund.create(params);
            log.info("Processed refund {} VND for PaymentIntent {} - Refund ID: {}",
                    amountInVND, paymentIntentId, refund.getId());
            return refund;
        } catch (StripeException e) {
            log.error("Failed to process refund for {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates customer for saving payment methods
     */
    public String createCustomer(User user) throws StripeException {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getFirstName() + " " + user.getLastName())
                    .putMetadata("user_id", user.getUserId().toString())
                    .putMetadata("username", user.getUsername())
                    .build();

            Customer customer = Customer.create(params);
            log.info("Created Stripe customer {} for user {}", customer.getId(), user.getUsername());
            return customer.getId();
        } catch (StripeException e) {
            log.error("Failed to create customer for user {}: {}", user.getUsername(), e.getMessage());
            throw e;
        }
    }

    /**
     * Creates COD payment intent for tracking purposes
     */
    public String createCODPaymentIntent(BigDecimal totalAmount, Long orderId) throws StripeException {
        try {
            long amountInVND = totalAmount.longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInVND)
                    .setCurrency(currency)
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                    .putMetadata("order_id", orderId.toString())
                    .putMetadata("payment_method", "cod")
                    .putMetadata("platform", "vestige")
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            log.info("Created COD PaymentIntent {} for order {} amount {} VND",
                    paymentIntent.getId(), orderId, amountInVND);
            return paymentIntent.getId();
        } catch (StripeException e) {
            log.error("Failed to create COD payment intent for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    /**
     * Gets platform account balance
     */
    public Balance getPlatformBalance() throws StripeException {
        try {
            Balance balance = Balance.retrieve();
            log.debug("Retrieved platform balance - Available: {}, Pending: {}",
                    balance.getAvailable(), balance.getPending());
            return balance;
        } catch (StripeException e) {
            log.error("Failed to retrieve platform balance: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Creates payout from platform account to bank
     */
    public Payout createPlatformPayout(BigDecimal amount, String description) throws StripeException {
        try {
            long amountInVND = amount.longValue();

            PayoutCreateParams params = PayoutCreateParams.builder()
                    .setAmount(amountInVND)
                    .setCurrency(currency)
                    .setDescription(description)
                    .putMetadata("platform", "vestige")
                    .putMetadata("type", "platform_fee_withdrawal")
                    .build();

            Payout payout = Payout.create(params);
            log.info("Created platform payout {} VND - ID: {}", amountInVND, payout.getId());
            return payout;
        } catch (StripeException e) {
            log.error("Failed to create platform payout: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves transfer details
     */
    public Transfer getTransferDetails(String transferId) throws StripeException {
        try {
            Transfer transfer = Transfer.retrieve(transferId);
            log.debug("Retrieved transfer details for {}", transferId);
            return transfer;
        } catch (StripeException e) {
            log.error("Failed to retrieve transfer {}: {}", transferId, e.getMessage());
            throw e;
        }
    }    /**
     * Validates webhook signatures for security
     */
    public Event validateWebhook(String payload, String sigHeader, String endpointSecret) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            log.info("Validated webhook event: {} (ID: {})", event.getType(), event.getId());
            return event;
        } catch (Exception e) {
            log.error("Webhook validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid webhook signature: " + e.getMessage());
        }
    }    /**
     * Handles webhook events for automated processing with idempotency
     */
    public void processWebhookEvent(Event event) {
        String eventId = event.getId();
        
        // Check if event was already processed (idempotency)
        if (processedEventIds.contains(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }
        
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);

        if (stripeObject == null) {
            log.error("Stripe object not found in webhook event data. Event ID: {}", eventId);
            return;
        }

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    if (stripeObject instanceof PaymentIntent paymentIntent) {
                        log.info("Payment succeeded via webhook for PI: {}", paymentIntent.getId());
                        orderService.handleSuccessfulPayment(paymentIntent.getId());
                    }
                    break;

                case "account.updated":
                    if (stripeObject instanceof Account account) {
                        log.info("Account updated via webhook for Account ID: {}", account.getId());
                        userService.handleStripeAccountUpdate(account);
                    }
                    break;

                case "charge.dispute.created":
                    if (stripeObject instanceof Dispute dispute) {
                        orderService.handleChargeDisputeCreated(dispute);
                    }
                    break;

                case "transfer.failed":
                    if (stripeObject instanceof Transfer transfer) {
                        orderService.handleTransferFailed(transfer);
                    }
                    break;

                case "transfer.created":
                    if (stripeObject instanceof Transfer transfer) {
                        orderService.handleTransferCreated(transfer);
                    }
                    break;

                case "payout.created":
                    if (stripeObject instanceof Payout payout) {
                        // Ghi log là đủ cho nhu cầu hiện tại
                        log.info("Payout {} of {} {} created for seller. Destination: {}",
                                payout.getId(), payout.getAmount(), payout.getCurrency().toUpperCase(), payout.getDestination());
                    }
                    break;                default:
                    log.debug("Unhandled webhook event: {} - Event ID: {}", event.getType(), eventId);
            }
            
            // Mark event as processed after successful handling
            processedEventIds.add(eventId);
            log.debug("Marked event {} as processed", eventId);
            
        } catch (Exception e) {
            log.error("Error processing webhook event {} (ID: {}): {}", event.getType(), eventId, e.getMessage(), e);
            // Don't mark as processed if there was an error, allow retry
            throw e;
        }
    }

    /**
     * Gets platform fee statistics
     */
    public Map<String, Object> getPlatformFeeStats() throws StripeException {
        try {
            Balance balance = getPlatformBalance();
            Map<String, Object> stats = new HashMap<>();

            // Calculate total available platform fees
            long totalAvailable = 0L;
            for (Balance.Available available : balance.getAvailable()) {
                if (currency.equals(available.getCurrency())) {
                    totalAvailable += available.getAmount();
                }
            }

            // Calculate total pending platform fees
            long totalPending = 0L;
            for (Balance.Pending pending : balance.getPending()) {
                if (currency.equals(pending.getCurrency())) {
                    totalPending += pending.getAmount();
                }
            }

            stats.put("available_platform_fees", totalAvailable);
            stats.put("pending_platform_fees", totalPending);
            stats.put("currency", currency);
            stats.put("last_updated", System.currentTimeMillis());

            log.debug("Platform fee stats - Available: {} VND, Pending: {} VND", totalAvailable, totalPending);
            return stats;
        } catch (StripeException e) {
            log.error("Failed to get platform fee stats: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get the current status of a PaymentIntent for debugging/monitoring
     */
    public String getPaymentIntentStatus(String paymentIntentId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            log.info("PaymentIntent {} current status: {}, last_payment_error: {}", 
                paymentIntentId, paymentIntent.getStatus(), 
                paymentIntent.getLastPaymentError() != null ? paymentIntent.getLastPaymentError().getMessage() : "none");
            return paymentIntent.getStatus();
        } catch (StripeException e) {
            log.error("Failed to retrieve PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Gets detailed payment status for a PaymentIntent
     */
    public String getPaymentStatus(String paymentIntentId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            return paymentIntent.getStatus();
        } catch (StripeException e) {
            log.error("Failed to get payment status for {}: {}", paymentIntentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Determines the appropriate capabilities for a Stripe account based on country
     * Vietnam: Only supports transfers
     * Other countries: May support both card_payments and transfers
     */
    private AccountCreateParams.Capabilities buildCapabilitiesForCountry(String country) {
        AccountCreateParams.Capabilities.Builder capabilitiesBuilder = 
                AccountCreateParams.Capabilities.builder();
        
        // Vietnam only supports transfers capability
        if ("VN".equals(country)) {
            capabilitiesBuilder.setTransfers(
                    AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true)
                            .build()
            );
        } else {
            // Other countries may support both capabilities
            capabilitiesBuilder
                    .setCardPayments(
                            AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true)
                                    .build()
                    )
                    .setTransfers(
                            AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true)
                                    .build()
                    );
        }
        
        return capabilitiesBuilder.build();
    }

    /**
     * Determines the appropriate service agreement based on capabilities
     */
    private String getServiceAgreementForCountry(String country) {
        // Vietnam uses recipient agreement (transfers only)
        if ("VN".equals(country)) {
            return "recipient";
        }
        // Other countries use full agreement (card payments + transfers)
        return "full";
    }

    /**
     * Transfer funds to a seller's Stripe account
     * 
     * @param amount The amount to transfer in the base currency
     * @param destinationAccountId The Stripe account ID of the seller
     * @param transactionId The transaction ID for tracking
     * @return The Stripe Transfer ID
     * @throws StripeException If the transfer fails
     */
    public String transferToSeller(BigDecimal amount, String destinationAccountId, Long transactionId) throws StripeException {
        try {
            // Convert amount to cents (or smallest currency unit)
            long amountInSmallestUnit = amount.longValue();
            
            Map<String, Object> params = new HashMap<>();
            params.put("amount", amountInSmallestUnit);
            params.put("currency", currency);
            params.put("destination", destinationAccountId);
            params.put("transfer_group", "TRANSACTION_" + transactionId);
            
            // Add metadata for tracking
            Map<String, String> metadata = new HashMap<>();
            metadata.put("transaction_id", transactionId.toString());
            metadata.put("platform", "vestige");
            metadata.put("type", "seller_payout");
            params.put("metadata", metadata);
            
            Transfer transfer = Transfer.create(params);
            log.info("Created transfer {} to account {} for amount {} {} (transaction {})",
                    transfer.getId(), destinationAccountId, amountInSmallestUnit, currency, transactionId);
            
            return transfer.getId();
        } catch (StripeException e) {
            log.error("Failed to transfer funds to seller account {}: {} (transaction {})",
                    destinationAccountId, e.getMessage(), transactionId);
            throw e;
        }
    }
}