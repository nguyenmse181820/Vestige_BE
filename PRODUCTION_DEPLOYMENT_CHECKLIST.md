# 🚀 Vestige Stripe Production Deployment Checklist

## 📋 Pre-Deployment Checklist

### 1. Stripe Configuration ✅
- [ ] **Stripe Dashboard Setup**
  - [ ] Activate live mode in Stripe dashboard
  - [ ] Complete business verification
  - [ ] Set up bank account for payouts
  - [ ] Configure webhook endpoint: `https://your-domain.com/api/stripe/webhook`
  - [ ] Subscribe to required webhook events:
    - `payment_intent.succeeded`
    - `payment_intent.payment_failed` 
    - `account.updated`
    - `charge.dispute.created`
    - `transfer.created`
    - `transfer.failed`
    - `payout.created`

- [ ] **API Keys**
  - [ ] Copy live secret key (starts with `sk_live_`)
  - [ ] Copy live publishable key (starts with `pk_live_`)
  - [ ] Copy webhook signing secret (starts with `whsec_`)

### 2. Code Changes Applied ✅
- [x] **Fixed Critical Bugs**
  - [x] Fixed seller account creation bug (was using buyer instead of seller)
  - [x] Fixed VND currency handling (removed cent multiplication)
  - [x] Enhanced Express account creation with Vietnam-specific settings

- [x] **Production Enhancements**
  - [x] Added proper merchant category code (5734 for second-hand goods)
  - [x] Added Vietnam country code
  - [x] Added card payment capabilities
  - [x] Added Terms of Service acceptance

### 3. Environment Configuration
- [ ] **Production Environment File**
  - [ ] Copy `.env.production.template` to `.env.production`
  - [ ] Update all placeholder values with actual production values
  - [ ] Set `SPRING_PROFILES_ACTIVE=prod`
  - [ ] Configure production database URL
  - [ ] Generate new JWT secret (256-bit)
  - [ ] Set production frontend/backend URLs

### 4. Infrastructure Requirements
- [ ] **SSL Certificate**
  - [ ] Valid SSL certificate for your domain
  - [ ] HTTPS enabled for all endpoints
  - [ ] SSL certificate trusted by Stripe

- [ ] **Database**
  - [ ] Production database setup and accessible
  - [ ] Database migrations applied
  - [ ] Backup strategy in place

- [ ] **Monitoring**
  - [ ] Error logging configured
  - [ ] Payment monitoring alerts set up
  - [ ] Webhook delivery monitoring

## 🧪 Testing Phase

### 1. Staging Environment Testing
- [ ] **Deploy to Staging with Live Keys**
  - [ ] Use live Stripe keys in staging environment
  - [ ] Test with small amounts (minimum Stripe allows)
  - [ ] Verify webhook delivery works
  - [ ] Test seller onboarding flow

- [ ] **Test Scenarios**
  - [ ] Complete buyer purchase flow ($1-5 USD equivalent)
  - [ ] Seller Express account creation and verification
  - [ ] Webhook event processing
  - [ ] Escrow hold and release
  - [ ] Order cancellation and refunds
  - [ ] Platform fee collection
  - [ ] Multi-seller order handling

### 2. Payment Flow Validation
```bash
# Test these payment scenarios:
1. ✅ Successful payment with valid card
2. ✅ Failed payment with invalid card  
3. ✅ Seller onboarding completion
4. ✅ Webhook event delivery
5. ✅ Escrow funds release (after 7 days)
6. ✅ Refund processing
7. ✅ Platform fee deduction
8. ✅ Multi-seller order fee splitting
```

## 🚀 Production Deployment

### 1. Deployment Steps
- [ ] **Pre-deployment**
  - [ ] Final code review
  - [ ] All tests passing
  - [ ] Staging environment validated
  - [ ] Database backup created

- [ ] **Deploy Application**
  - [ ] Deploy with production environment variables
  - [ ] Verify application starts successfully
  - [ ] Check health endpoints
  - [ ] Verify database connectivity

- [ ] **Stripe Configuration**
  - [ ] Update webhook endpoint URL in Stripe dashboard
  - [ ] Test webhook delivery
  - [ ] Verify live keys are working

### 2. Post-Deployment Validation
- [ ] **Immediate Checks (First 15 minutes)**
  - [ ] Application health check passes
  - [ ] Database connections working
  - [ ] Stripe webhook test delivery successful
  - [ ] Error logs are clean

- [ ] **First Transaction Test**
  - [ ] Process a small test transaction
  - [ ] Verify payment success
  - [ ] Check webhook event received
  - [ ] Validate database updates
  - [ ] Confirm seller account creation works

## 📊 Monitoring & Alerts

### 1. Key Metrics to Monitor
- [ ] **Payment Success Rate**
  - Target: >95% success rate
  - Alert if drops below 90%

- [ ] **Webhook Delivery Rate** 
  - Target: >99% delivery rate
  - Alert if any webhook fails

- [ ] **Seller Onboarding Rate**
  - Monitor completion percentage
  - Alert on unusual drops

- [ ] **Platform Fee Collection**
  - Monitor fee collection accuracy
  - Alert on any discrepancies

### 2. Error Monitoring
- [ ] **Payment Failures**
  - Monitor declined payments
  - Track failure reasons
  - Alert on unusual patterns

- [ ] **API Errors**
  - Stripe API error rates
  - Database connection errors
  - Application exceptions

## 🔧 Troubleshooting Guide

### Common Issues and Solutions

#### 1. Webhook Not Receiving Events
```bash
# Check webhook endpoint
curl -X POST https://your-domain.com/api/stripe/webhook \
  -H "Content-Type: application/json" \
  -d '{"test": "webhook"}'

# Verify in Stripe dashboard:
# - Webhook endpoint URL is correct
# - Events are subscribed
# - SSL certificate is valid
```

#### 2. Payment Intent Creation Fails
```bash
# Check common issues:
# - Live API keys are set correctly
# - Account has required capabilities
# - Amount is in correct format for VND
# - Seller has completed onboarding
```

#### 3. Seller Cannot Receive Payments
```bash
# Verify seller account status:
# - Express account is fully onboarded
# - Charges and payouts are enabled
# - Required information provided
# - Terms of service accepted
```

## 📞 Emergency Contacts

### Rollback Plan
- [ ] **Database Rollback**
  - [ ] Restore from pre-deployment backup
  - [ ] Verify data integrity

- [ ] **Application Rollback**
  - [ ] Deploy previous stable version
  - [ ] Switch back to test Stripe keys if needed
  - [ ] Update webhook endpoints

### Support Information
- **Stripe Support**: https://support.stripe.com
- **Vietnam Stripe Documentation**: https://stripe.com/docs/connect/express-accounts
- **Webhook Testing**: https://stripe.com/docs/webhooks/test

## 🎯 Success Criteria

### Go-Live Criteria
- [x] All code changes applied and tested
- [ ] All checklist items completed
- [ ] Test transactions successful in staging
- [ ] Monitoring and alerts configured
- [ ] Emergency rollback plan ready

### Post-Launch Success Metrics (First 24 hours)
- [ ] Zero payment-related errors
- [ ] All webhooks delivered successfully  
- [ ] At least one successful end-to-end transaction
- [ ] Seller onboarding working correctly
- [ ] Platform fees collected accurately

### Week 1 Success Metrics
- [ ] Payment success rate >95%
- [ ] Webhook delivery rate >99%
- [ ] No escalated payment issues
- [ ] Seller satisfaction with onboarding
- [ ] Platform fee collection accuracy >99%

---

## 🏁 Final Notes

**Before going live:**
1. Have your rollback plan ready
2. Monitor closely for the first few hours
3. Process a few small test transactions
4. Have someone available to handle any issues

**Remember:** Start with small transaction amounts and gradually increase as confidence builds.

**Emergency Stop:** If any critical issues arise, you can immediately switch back to test mode by updating the Stripe API keys in your environment variables.
