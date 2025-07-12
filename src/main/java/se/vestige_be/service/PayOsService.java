package se.vestige_be.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.vestige_be.exception.BusinessLogicException;
import se.vestige_be.pojo.MembershipPlan;
import se.vestige_be.pojo.User;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import vn.payos.type.PaymentLinkData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOsService {

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    private final Gson gson = new Gson();

    /**
     * Creates a payment link for subscription using PayOS
     * Uses Return URL method for secure payment confirmation
     */
    public CreatePaymentLinkResult createPaymentLink(User user, MembershipPlan plan) {
        try {
            PayOS payOS = new PayOS(clientId, apiKey, checksumKey);
            String orderCode = generateOrderCode(user.getUserId());
            String itemName = createItemName(plan.getName());
            ItemData item = ItemData.builder()
                    .name(itemName)
                    .quantity(1)
                    .price(plan.getPrice().intValue())
                    .build();

            // Create payment data with truncated description (PayOS limit: 25 characters)
            String description = createPaymentDescription(plan.getName(), user.getUsername());
                      
            PaymentData paymentData = PaymentData.builder()
                    .orderCode(Long.parseLong(orderCode))
                    .amount(plan.getPrice().intValue())
                    .description(description)
                    .items(Arrays.asList(item))
                    .cancelUrl(baseUrl + "/api/v1/payos/payment-callback")
                    .returnUrl(baseUrl + "/api/v1/payos/payment-callback")
                    .build();

            // Create payment link
            CheckoutResponseData result = payOS.createPaymentLink(paymentData);
            
            log.info("Created PayOS payment link for user {} and plan {}: orderCode={}, checkoutUrl={}", 
                    user.getUsername(), plan.getName(), orderCode, result.getCheckoutUrl());
            
            return CreatePaymentLinkResult.builder()
                    .checkoutUrl(result.getCheckoutUrl())
                    .orderCode(orderCode)
                    .qrCode(result.getQrCode())
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for user {} and plan {}: {}", 
                    user.getUsername(), plan.getName(), e.getMessage());
            throw new BusinessLogicException("Failed to create payment link: " + e.getMessage());
        }
    }

    /**
     * Verifies webhook signature from PayOS
     */
    public boolean verifyWebhookSignature(String signature, String webhookBody) {
        try {
            if (signature == null || webhookBody == null) {
                log.warn("Missing signature or webhook body for PayOS webhook verification");
                return false;
            }
            
            // Generate expected signature using HMAC-SHA256
            String expectedSignature = generateHmacSignature(webhookBody, checksumKey);
            
            boolean isValid = signature.equals(expectedSignature);
            log.debug("PayOS webhook signature verification: {} (expected: {}, received: {})", 
                    isValid ? "VALID" : "INVALID", expectedSignature, signature);
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error verifying PayOS webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses PayOS webhook data to extract payment information
     */
    public PayOsWebhookData parseWebhookData(String webhookBody) {
        try {
            JsonObject jsonObject = gson.fromJson(webhookBody, JsonObject.class);
            
            // Extract data from PayOS webhook structure
            JsonObject data = jsonObject.getAsJsonObject("data");
            if (data == null) {
                throw new BusinessLogicException("Invalid webhook data structure");
            }
            
            String orderCode = data.get("orderCode").getAsString();
            String status = data.get("status").getAsString();
            String transactionId = data.has("transactionId") ? data.get("transactionId").getAsString() : null;
            Integer amount = data.has("amount") ? data.get("amount").getAsInt() : null;
            
            log.debug("Parsed PayOS webhook data: orderCode={}, status={}, transactionId={}, amount={}", 
                    orderCode, status, transactionId, amount);
            
            return PayOsWebhookData.builder()
                    .orderCode(orderCode)
                    .status(status)
                    .transactionId(transactionId)
                    .amount(amount)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to parse PayOS webhook data: {}", e.getMessage());
            throw new BusinessLogicException("Failed to parse webhook data: " + e.getMessage());
        }
    }

    /**
     * Verifies the actual status of a payment link with PayOS server.
     * This is crucial for security - we cannot trust the status from URL parameters alone.
     * @param orderCode The order code to verify
     * @return true if the transaction status is PAID, false otherwise.
     */
    public boolean verifyPaymentStatus(long orderCode) {
        try {
            PayOS payOS = new PayOS(clientId, apiKey, checksumKey);
            // Get payment information from PayOS
            PaymentLinkData paymentInfo = payOS.getPaymentLinkInformation(orderCode);
            
            if (paymentInfo != null && "PAID".equals(paymentInfo.getStatus())) {
                log.info("Server-side verification PASSED for orderCode {}. Status is PAID.", orderCode);
                return true;
            }
            
            log.warn("Server-side verification FAILED for orderCode {}. Status is {}.", 
                    orderCode, paymentInfo != null ? paymentInfo.getStatus() : "NOT_FOUND");
            return false;
            
        } catch (Exception e) {
            log.error("Error during server-side payment verification for orderCode {}: {}", orderCode, e.getMessage());
            return false;
        }
    }

    /**
     * Generates a unique order code using timestamp and user ID
     */
    private String generateOrderCode(Long userId) {
        // Use current timestamp in seconds + user ID to ensure uniqueness
        // PayOS requires order code to be a number, so we'll use timestamp + user ID
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        String orderCode = String.valueOf(timestamp).substring(5) + String.format("%03d", userId % 1000);
        log.debug("Generated order code: {} for user ID: {}", orderCode, userId);
        return orderCode;
    }

    /**
     * Generates HMAC-SHA256 signature for webhook verification
     */
    private String generateHmacSignature(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }

    /**
     * Creates a payment description that fits PayOS 25-character limit
     */
    private String createPaymentDescription(String planName, String username) {
        // PayOS has a 25-character limit for description
        final int MAX_LENGTH = 25;
        
        // Try different formats to fit within the limit
        String description = planName + " - " + username;
        if (description.length() <= MAX_LENGTH) {
            return description;
        }
        
        // If too long, try just the plan name
        if (planName.length() <= MAX_LENGTH) {
            return planName;
        }
        
        // If plan name is still too long, truncate it
        return planName.substring(0, MAX_LENGTH - 3) + "...";
    }

    /**
     * Creates an item name that fits PayOS limits (typically 50 characters for item names)
     */
    private String createItemName(String planName) {
        final int MAX_LENGTH = 50;
        String itemName = planName + " Membership";
        
        if (itemName.length() <= MAX_LENGTH) {
            return itemName;
        }
        
        // If too long, try just the plan name
        if (planName.length() <= MAX_LENGTH) {
            return planName;
        }
        
        // If plan name is still too long, truncate it
        return planName.substring(0, MAX_LENGTH - 3) + "...";
    }

    /**
     * Result class for payment link creation
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreatePaymentLinkResult {
        private String checkoutUrl;
        private String orderCode;
        private String qrCode;
    }

    /**
     * Data class for PayOS webhook information
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PayOsWebhookData {
        private String orderCode;
        private String status;
        private String transactionId;
        private Integer amount;
    }
}
