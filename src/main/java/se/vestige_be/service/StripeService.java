package se.vestige_be.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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

@Service
@Slf4j
public class StripeService {

    @Value("${stripe.api.secret-key}")
    private String secretKey;

    @Value("${stripe.currency:vnd}")
    private String currency;

    private final OrderService orderService;
    private final UserService userService;

    public StripeService(@Lazy OrderService orderService, @Lazy UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe initialized with currency: {}", currency);
    }

    /**
     * Creates a Stripe Express account for seller onboarding
     */
    public Account createExpressAccount(User user) throws StripeException {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setEmail(user.getEmail())
                    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                    .setBusinessProfile(
                            AccountCreateParams.BusinessProfile.builder()
                                    .setName(user.getFirstName() + " " + user.getLastName())
                                    .build()
                    )
                    .setCapabilities(
                            AccountCreateParams.Capabilities.builder()
                                    .setTransfers(
                                            AccountCreateParams.Capabilities.Transfers.builder()
                                                    .setRequested(true)
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("user_id", user.getUserId().toString())
                    .putMetadata("username", user.getUsername())
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
     * Verifies that a payment was successful
     */
    public boolean verifyPayment(String paymentIntentId) throws StripeException {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            boolean succeeded = "succeeded".equals(paymentIntent.getStatus());
            log.info("Payment verification for {}: {}", paymentIntentId, succeeded ? "SUCCESS" : "FAILED");
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
    }

    /**
     * Validates webhook signatures for security
     */
    public Event validateWebhook(String payload, String sigHeader, String endpointSecret) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            log.info("Validated webhook event: {}", event.getType());
            return event;
        } catch (Exception e) {
            log.error("Webhook validation failed: {}", e.getMessage());
            throw new BusinessLogicException("Invalid webhook signature");
        }
    }

    /**
     * Handles webhook events for automated processing
     */
    public void processWebhookEvent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);

        if (stripeObject == null) {
            log.error("Stripe object not found in webhook event data. Event ID: {}", event.getId());
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
                    break;

                default:
                    log.debug("Unhandled webhook event: {} - Event ID: {}", event.getType(), event.getId());
            }
        } catch (Exception e) {
            log.error("Error processing webhook event {} (ID: {}): {}", event.getType(), event.getId(), e.getMessage(), e);
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
}