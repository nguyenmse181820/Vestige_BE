# Stripe Production Migration Guide

## Current Testing Configuration

### 1. **Environment Variables (Testing)**
```yaml
# Current configuration in application.yml
stripe:
  api:
    secret-key: ${STRIPE_SECRET_KEY}        # Currently using test keys
    publishable-key: ${STRIPE_PUBLISHABLE_KEY}  # Currently using test keys
  webhook:
    secret: ${STRIPE_WEBHOOK_SECRET}        # Currently using test webhook secret
```

### 2. **Webhook Configuration (Local Testing)**
- Current webhook endpoint: `/api/stripe/webhook`
- Using local webhook secret for testing
- No signature validation bypass (good security practice)

### 3. **Test Data Patterns**
The following suggest test mode usage:
- Hardcoded VND currency (good for Vietnam market)
- Development URLs in `frontendUrl` configuration
- Express account creation without production verification

## Required Changes for Production

### 1. **Stripe API Keys Migration**

**Current (Test):**
```bash
# Test keys (start with sk_test_ and pk_test_)
STRIPE_SECRET_KEY=sk_test_xxxxxxxxxxxxx
STRIPE_PUBLISHABLE_KEY=pk_test_xxxxxxxxxxxxx
```

**Production Required:**
```bash
# Live keys (start with sk_live_ and pk_live_)
STRIPE_SECRET_KEY=sk_live_xxxxxxxxxxxxx
STRIPE_PUBLISHABLE_KEY=pk_live_xxxxxxxxxxxxx
```

### 2. **Webhook Endpoints**

**Current (Testing):**
```bash
# Local development webhook
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxx
```

**Production Required:**
```bash
# Production webhook endpoint
# Must be publicly accessible HTTPS URL
# Example: https://api.vestige.com/api/stripe/webhook
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxx_live
```

### 3. **Critical Code Changes Required**

#### A. Express Account Creation (StripeService.java)
**Current Issue:**

**Production Fix:**
```java
AccountCreateParams params = AccountCreateParams.builder()
    .setType(AccountCreateParams.Type.EXPRESS)
    .setCountry("VN")  // ✅ Required for Vietnam
    .setEmail(user.getEmail())
    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
    .setBusinessProfile(
        AccountCreateParams.BusinessProfile.builder()
            .setName(user.getFirstName() + " " + user.getLastName())
            .setMcc("5734")  // ✅ Merchant Category Code for second-hand goods
            .setProductDescription("Second-hand fashion and electronics marketplace")
            .setUrl("https://vestige.com/seller/" + user.getUserId())
            .build()
    )
    .setCapabilities(
        AccountCreateParams.Capabilities.builder()
            .setCardPayments(
                AccountCreateParams.Capabilities.CardPayments.builder()
                    .setRequested(true)
                    .build()
            )
            .setTransfers(
                AccountCreateParams.Capabilities.Transfers.builder()
                    .setRequested(true)
                    .build()
            )
            .build()
    )
    .setTosAcceptance(
        AccountCreateParams.TosAcceptance.builder()
            .setServiceAgreement("recipient")
            .build()
    )
    // Add required business information for Vietnam
    .build();
```

#### B. Payment Intent Creation Bug Fix (StripePaymentService.java)
**Critical Bug Found:**
```java
// Line 203 - MAJOR BUG
String sellerStripeAccountId = getOrCreateSellerStripeAccount(order.getBuyer()); 
// ❌ Should be order.getSeller(), not order.getBuyer()
```

**Fix Required:**
```java
// Get the seller from the product, not the buyer
User seller = transaction.getSeller();
String sellerStripeAccountId = getOrCreateSellerStripeAccount(seller);
```

#### C. Currency Handling Enhancement
**Current:**
```java
// Lines 204-205 in StripePaymentService.java
long amountInCents = order.getTotalAmount().multiply(new BigDecimal("100")).longValue();
// ❌ VND doesn't use cents/decimals
```

**Production Fix:**
```java
// VND is zero-decimal currency
long amountInVND = order.getTotalAmount().longValue();
// No multiplication by 100 needed for VND
```

### 4. **Infrastructure Changes**

#### A. Webhook URL Configuration
**Production Setup Required:**
1. **Public HTTPS endpoint**: `https://your-domain.com/api/stripe/webhook`
2. **SSL Certificate**: Must be valid and trusted
3. **Webhook Events to Subscribe:**
   ```
   payment_intent.succeeded
   payment_intent.payment_failed
   account.updated
   charge.dispute.created
   transfer.created
   transfer.failed
   payout.created
   ```

#### B. Environment Variables
**Production Environment (.env.production):**
```bash
# Stripe Live Configuration
STRIPE_SECRET_KEY=sk_live_xxxxxxxxxxxxx
STRIPE_PUBLISHABLE_KEY=pk_live_xxxxxxxxxxxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxx

# Production URLs
FRONTEND_URL=https://vestige.com
BACKEND_URL=https://api.vestige.com

# Database
DATABASE_URL=your_production_db_url
DATABASE_USERNAME=your_production_username
DATABASE_PASSWORD=your_production_password

# Security
JWT_SECRET=your_256_bit_production_secret
```

### 5. **Legal & Compliance Requirements**

#### A. Terms of Service Updates
- Update Stripe Terms of Service acceptance
- Add Vietnam-specific legal requirements
- Implement KYC (Know Your Customer) for sellers

#### B. Tax Handling
```java
// Add to StripeService.java for Vietnam tax compliance
private void addTaxInfo(SessionCreateParams.Builder builder, BigDecimal amount) {
    // Vietnam VAT handling if required
    BigDecimal vatAmount = amount.multiply(new BigDecimal("0.10")); // 10% VAT
    // Add tax information to session
}
```

### 6. **Testing Production Setup**

#### A. Stripe Test in Production Mode
1. Use live keys in staging environment first
2. Process small test transactions ($1-5 USD equivalent)
3. Test webhook delivery
4. Verify seller payouts work correctly

#### B. Key Test Scenarios
```bash
# Test these scenarios before going live:
1. Complete buyer payment flow
2. Seller onboarding and verification
3. Escrow hold and release (7-day period)
4. Refund processing
5. Dispute handling
6. Platform fee collection
7. Multi-seller order splitting
```

### 7. **Monitoring & Alerting**

#### A. Add Production Logging
```java
// Enhanced logging for production
@Slf4j
public class StripeService {
    
    public PaymentIntent createPlatformCharge(BigDecimal totalAmount, Long orderId) throws StripeException {
        // Add structured logging for production monitoring
        log.info("Creating payment intent - Order: {}, Amount: {} VND, Environment: PRODUCTION", 
                orderId, totalAmount);
        
        // Add metrics collection
        try {
            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Payment intent created successfully - PI: {}, Order: {}", 
                    intent.getId(), orderId);
            return intent;
        } catch (StripeException e) {
            log.error("Payment intent creation failed - Order: {}, Error: {}", 
                    orderId, e.getMessage(), e);
            // Send alert to monitoring system
            throw e;
        }
    }
}
```

### 8. **Security Enhancements**

#### A. Webhook Security (Already Good)
```java
// Current implementation is secure ✅
public Event validateWebhook(String payload, String sigHeader, String endpointSecret) {
    try {
        Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        return event;
    } catch (Exception e) {
        throw new BusinessLogicException("Invalid webhook signature");
    }
}
```

#### B. Rate Limiting (Add)
```java
// Add rate limiting for payment endpoints
@RateLimiter(name = "payment", fallbackMethod = "paymentRateLimitFallback")
public PaymentResponse createPayment(Long buyerId, OrderCreateRequest request) {
    // Existing implementation
}
```

## Implementation Checklist

### Pre-Production Checklist
- [ ] Update Stripe API keys to live keys
- [ ] Fix seller account creation bug (line 203 in StripePaymentService.java)
- [ ] Fix VND currency handling (remove *100 multiplication)
- [ ] Update webhook URL to production HTTPS endpoint
- [ ] Add proper error handling and logging
- [ ] Implement rate limiting
- [ ] Add monitoring and alerting
- [ ] Update Terms of Service acceptance
- [ ] Test all payment flows in staging with live keys

### Production Deployment Checklist
- [ ] Deploy with production environment variables
- [ ] Configure Stripe webhook endpoint
- [ ] Test webhook delivery
- [ ] Monitor first few transactions closely
- [ ] Verify seller payouts work
- [ ] Check platform fee collection
- [ ] Monitor error rates and latency

### Post-Production Monitoring
- [ ] Monitor payment success rates
- [ ] Track webhook delivery failures
- [ ] Monitor dispute rates
- [ ] Check seller onboarding completion rates
- [ ] Monitor platform fee collection accuracy

## Summary

Your current implementation is quite comprehensive and well-structured. The main changes needed are:

1. **Environment Configuration**: Switch from test to live Stripe keys
2. **Bug Fixes**: Critical seller account bug in payment creation
3. **Currency Handling**: Fix VND amount calculation
4. **Webhook Setup**: Configure production webhook endpoint
5. **Compliance**: Add Vietnam-specific requirements

The escrow system, fee collection, and webhook handling are already production-ready. With these changes, your payment system will be ready for real transactions.
