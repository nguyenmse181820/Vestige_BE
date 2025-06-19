# 🎯 Demo Environment Setup for Stripe Test Payments

## Required Environment Variables

Create a `.env` file or set these environment variables for your demo:

```bash
# Stripe Test Keys (Get these from https://dashboard.stripe.com/test/apikeys)
STRIPE_SECRET_KEY=sk_test_...  # Your Stripe test secret key
STRIPE_PUBLISHABLE_KEY=pk_test_...  # Your Stripe test publishable key
STRIPE_WEBHOOK_SECRET=whsec_...  # Webhook endpoint secret (optional for basic demo)

# Database Configuration
DATABASE_URL=jdbc:postgresql://localhost:5432/vestige_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=12345

# App Configuration
APP_FRONTEND_URL=http://localhost:3000
```

## 🧪 Test Payment Methods for Demo

### 1. **Successful Payment Cards**
```javascript
// Use these test card numbers in your frontend
const testCards = {
  visa: '4242424242424242',
  visaDebit: '4000056655665556',
  mastercard: '5555555555554444',
  amex: '378282246310005',
  // Any future expiry date (e.g., 12/34)
  // Any 3-digit CVC (e.g., 123)
  // Any valid postal code
};
```

### 2. **Declined Payment Cards (for error testing)**
```javascript
const declinedCards = {
  genericDecline: '4000000000000002',
  insufficientFunds: '4000000000009995',
  lostCard: '4000000000009987',
  stolenCard: '4000000000009979'
};
```

### 3. **3D Secure Test Cards (for authentication testing)**
```javascript
const threeDSecureCards = {
  authRequired: '4000002500003155',  // Requires authentication
  authOptional: '4000002760003184'   // Optional authentication
};
```

## 💡 Demo Script for Tomorrow

### Step 1: Product Creation
1. Create a test product (e.g., "Demo Item - ₫100,000")
2. Set up seller account with test Stripe Express account

### Step 2: Order Creation
1. Add item to cart
2. Create order with Stripe payment method
3. Show PaymentIntent creation in logs

### Step 3: Payment Flow Demo
1. **Frontend**: Use test card `4242424242424242`
2. **Expiry**: Any future date (e.g., `12/34`)
3. **CVC**: Any 3 digits (e.g., `123`)
4. **Postal Code**: Any valid code (e.g., `12345`)

### Step 4: Show Success Flow
1. Payment succeeds instantly
2. Order status changes to PAID
3. Product status changes to SOLD
4. Transaction record created

### Step 5: Demo Admin Features
1. View all orders in admin panel
2. Process test refund
3. Show order statistics

## 🚀 Quick Start Commands

```bash
# 1. Set environment variables
export STRIPE_SECRET_KEY="sk_test_YOUR_KEY"
export STRIPE_PUBLISHABLE_KEY="pk_test_YOUR_KEY"

# 2. Start the application
mvn spring-boot:run

# 3. Test payment endpoint
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "items": [{"productId": 1, "notes": "Demo order"}],
    "shippingAddressId": 1,
    "paymentMethod": "STRIPE_CARD"
  }'
```

## 📱 Frontend Integration

Make sure your frontend uses the test publishable key:

```javascript
// In your React/Vue/Angular app
const stripe = Stripe('pk_test_YOUR_PUBLISHABLE_KEY');

// Test payment data
const testPaymentData = {
  number: '4242424242424242',
  exp_month: 12,
  exp_year: 2034,
  cvc: '123'
};
```

## 🔍 Monitoring During Demo

Watch these logs to show the payment flow:
```bash
# Look for these log entries:
[INFO] Created PaymentIntent pi_xxx for order 123 amount 100000 VND
[INFO] Payment verification successful for PI pi_xxx order 123 amount 100000
[INFO] Order 123 successfully updated after payment
```

## 💳 Demo Amounts

Use these Vietnamese Dong amounts for realistic demo:
- Small item: ₫50,000 (50000)
- Medium item: ₫200,000 (200000) 
- Large item: ₫1,000,000 (1000000)

## ⚠️ Important Notes

1. **Test Mode Only**: All transactions are fake - no real money
2. **Instant Success**: Test payments succeed immediately
3. **Webhook Testing**: Use ngrok for webhook testing if needed
4. **Data Reset**: Test data can be cleared from Stripe dashboard

## 🎬 Demo Flow Checklist

- [ ] Environment variables set
- [ ] Application starts successfully
- [ ] Can create orders with Stripe payment
- [ ] Test card payments work
- [ ] Order status updates correctly
- [ ] Admin refund functionality works
- [ ] Error handling demonstrates properly
- [ ] Logs show payment flow clearly
