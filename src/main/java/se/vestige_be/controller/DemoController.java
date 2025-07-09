package se.vestige_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.vestige_be.dto.response.ApiResponse;

import java.util.Map;

/**
 * Demo controller for testing payment flows
 * Only available in development/test profiles
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "default"})
@Tag(name = "Demo & Testing", 
     description = "Demo endpoints for testing payment flows. Only available in development mode.")
public class DemoController {

    @Operation(
        summary = "Get test payment cards",
        description = "Returns test credit card numbers for demo purposes. These cards work only in Stripe test mode."
    )
    @GetMapping("/test-cards")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTestCards() {
        
        Map<String, Object> testCards = Map.of(
            "successful_payments", Map.of(
                "visa", "4242424242424242",
                "visa_debit", "4000056655665556", 
                "mastercard", "5555555555554444",
                "american_express", "378282246310005"
            ),
            "declined_payments", Map.of(
                "generic_decline", "4000000000000002",
                "insufficient_funds", "4000000000009995",
                "lost_card", "4000000000009987",
                "stolen_card", "4000000000009979"
            ),
            "test_details", Map.of(
                "expiry_month", 12,
                "expiry_year", 2034,
                "cvc", "123",
                "postal_code", "12345"
            ),
            "currency", "vnd",
            "demo_amounts", Map.of(
                "small_item", 50000,   // ₫50,000
                "medium_item", 200000,  // ₫200,000  
                "large_item", 1000000   // ₫1,000,000
            ),
            "note", "These are test cards for Stripe test mode only. No real money will be charged."
        );
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
            .message("Test payment cards retrieved successfully")
            .data(testCards)
            .build());
    }

    @Operation(
        summary = "Demo payment flow status",
        description = "Shows the current demo environment status and configuration"
    )
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDemoStatus() {
        
        String stripeMode = System.getenv("STRIPE_SECRET_KEY") != null && 
                           System.getenv("STRIPE_SECRET_KEY").startsWith("sk_test_") 
                           ? "TEST_MODE" : "UNKNOWN";
        
        Map<String, Object> status = Map.of(
            "demo_ready", true,
            "stripe_mode", stripeMode,
            "endpoints", Map.of(
                "create_order", "/api/orders",
                "confirm_payment", "/api/orders/{orderId}/confirm-payment",
                "admin_orders", "/api/orders/admin/all",
                "admin_refund", "/api/orders/admin/{orderId}/refund",
                "swagger_ui", "/swagger-ui/index.html"
            ),
            "demo_flow", Map.of(
                "step_1", "Create order with STRIPE_CARD payment method",
                "step_2", "Use test card 4242424242424242 in frontend",
                "step_3", "Confirm payment via API or webhook",
                "step_4", "Check order status change to PAID",
                "step_5", "Demo admin features (refunds, statistics)"
            ),
            "monitoring_tips", Map.of(
                "logs_to_watch", "PaymentIntent creation, verification, order updates",
                "database_check", "Check orders and transactions tables",
                "stripe_dashboard", "View test payments in Stripe dashboard"
            )
        );
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
            .message("Demo environment status")
            .data(status)
            .build());
    }

    @Operation(
        summary = "Test payment scenarios",
        description = "Get different payment test scenarios for comprehensive demo"
    )
    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTestScenarios() {
        
        Map<String, Object> scenarios = Map.of(
            "successful_payment", Map.of(
                "card", "4242424242424242",
                "description", "Standard successful payment flow",
                "expected_result", "Order status PAID, product status SOLD"
            ),
            "declined_payment", Map.of(
                "card", "4000000000000002", 
                "description", "Payment declined by bank",
                "expected_result", "Payment fails, order remains PENDING"
            ),
            "3d_secure", Map.of(
                "card", "4000002500003155",
                "description", "Requires 3D Secure authentication",
                "expected_result", "Additional authentication step required"
            ),
            "large_amount", Map.of(
                "card", "4242424242424242",
                "amount", 5000000,
                "description", "Large amount payment (₫5,000,000)",
                "expected_result", "Successful payment with higher amount"
            ),
            "admin_refund", Map.of(
                "description", "After successful payment, demo admin refund",
                "steps", "1. Complete payment, 2. Admin refund via API, 3. Check refund in Stripe",
                "expected_result", "Refund processed, order status updated"
            )
        );
        
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
            .message("Demo test scenarios")
            .data(scenarios)
            .build());
    }
}
