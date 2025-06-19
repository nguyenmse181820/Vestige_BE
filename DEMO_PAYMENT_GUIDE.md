# 🎯 Vestige Payment Demo Guide

## 📋 Demo Overview

This guide provides step-by-step instructions for demonstrating the Vestige payment system using **Stripe Test Mode** (no real money involved). The demo can be run locally or using Docker for team collaboration.

---

## 🚀 Deployment Options

### Option 1: Local Development
- Run backend locally with Maven/Spring Boot
- Frontend team connects to `http://localhost:8080`

### Option 2: Docker Deployment ⭐ **Recommended for Team Demo**
- Backend runs in Docker container
- Easy sharing via docker-compose
- Frontend team can easily spin up the full environment

---

## 🐳 Docker Demo Setup (Recommended)

### Prerequisites
- Docker and Docker Compose installed
- Stripe test account credentials
- Frontend application ready to connect

### Step 1: Environment Configuration

Create `.env` file in the project root:

```bash
# 🧪 STRIPE TEST KEYS (Get from https://dashboard.stripe.com/test/apikeys)
STRIPE_SECRET_KEY=sk_test_YOUR_TEST_SECRET_KEY_HERE
STRIPE_PUBLISHABLE_KEY=pk_test_YOUR_TEST_PUBLISHABLE_KEY_HERE
STRIPE_WEBHOOK_SECRET=whsec_YOUR_WEBHOOK_SECRET_HERE

# 🗄️ DATABASE CONFIG
DATABASE_URL=jdbc:postgresql://postgres:5432/vestige_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=12345

# 🌐 APP CONFIG
APP_FRONTEND_URL=http://localhost:3000

# 🔐 JWT CONFIG (Use secure values for demo)
JWT_SECRET=demo_jwt_secret_key_for_testing_only_2024
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=86400000
```

### Step 2: Docker Compose Startup

```bash
# Start the full environment
docker-compose up -d

# Check if services are running
docker-compose ps

# View logs
docker-compose logs -f vestige-backend
```

### Step 3: Verify Backend is Ready

```bash
# Health check
curl http://localhost:8080/actuator/health

# Test demo endpoints
curl http://localhost:8080/api/demo/test-cards
```

---

## 🧪 Test Payment Scenarios

### 1. Successful Payment Flow

**Test Card Details:**
```
Card Number: 4242424242424242
Expiry: 12/34 (any future date)
CVC: 123 (any 3 digits)
Postal Code: 12345 (any valid code)
```

**Expected Flow:**
1. ✅ Create order via API
2. ✅ Generate payment intent
3. ✅ Process payment with test card
4. ✅ Receive payment confirmation
5. ✅ Order status updates to PAID

### 2. Declined Payment Flow

**Test Card Details:**
```
Card Number: 4000000000000002
Expiry: 12/34
CVC: 123
Postal Code: 12345
```

**Expected Flow:**
1. ✅ Create order via API
2. ✅ Generate payment intent
3. ❌ Payment declined by Stripe
4. ❌ Order remains in PENDING status
5. ✅ Error message displayed to user

### 3. Authentication Required (3D Secure)

**Test Card Details:**
```
Card Number: 4000002500003155
Expiry: 12/34
CVC: 123
Postal Code: 12345
```

**Expected Flow:**
1. ✅ Create order via API
2. ✅ Generate payment intent
3. 🔐 3D Secure authentication popup
4. ✅ Complete authentication
5. ✅ Payment succeeds after authentication

---

## 📡 API Endpoints for Demo

### Core Payment Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Create new order |
| POST | `/api/stripe/create-payment-intent` | Initialize payment |
| POST | `/api/stripe/confirm-payment` | Confirm payment |
| GET | `/api/orders/{id}` | Check order status |

### Demo Helper Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/demo/test-cards` | Get test card numbers |
| GET | `/api/demo/scenarios` | Get demo scenarios |
| GET | `/api/demo/status` | Check demo environment |

### Admin Endpoints (for advanced demo)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/orders/admin/all` | View all orders |
| POST | `/api/orders/admin/{id}/refund` | Process refund |
| GET | `/api/orders/admin/stats` | System statistics |

---

## 🎬 Demo Script

### Phase 1: Environment Setup (2 minutes)

1. **Show Docker Status**
   ```bash
   docker-compose ps
   ```

2. **Verify Backend Health**
   ```bash
   curl http://localhost:8080/api/demo/status
   ```

3. **Display Test Cards**
   ```bash
   curl http://localhost:8080/api/demo/test-cards | jq
   ```

### Phase 2: Successful Payment Demo (5 minutes)

1. **Create Order**
   ```bash
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -d '{
       "productId": 1,
       "quantity": 2,
       "shippingAddressId": 1
     }'
   ```

2. **Create Payment Intent**
   ```bash
   curl -X POST http://localhost:8080/api/stripe/create-payment-intent \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -d '{
       "orderId": 1,
       "amount": 1000
     }'
   ```

3. **Frontend Payment Simulation**
   - Use test card: `4242424242424242`
   - Show Stripe Elements UI
   - Complete payment form

4. **Confirm Payment**
   ```bash
   curl -X POST http://localhost:8080/api/stripe/confirm-payment \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -d '{
       "paymentIntentId": "pi_xxx",
       "orderId": 1
     }'
   ```

5. **Verify Order Status**
   ```bash
   curl http://localhost:8080/api/orders/1 \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```

### Phase 3: Error Handling Demo (3 minutes)

1. **Declined Payment**
   - Use test card: `4000000000000002`
   - Show error handling in frontend
   - Verify order remains PENDING

2. **3D Secure Authentication**
   - Use test card: `4000002500003155`
   - Show authentication popup
   - Complete authentication flow

### Phase 4: Admin Features Demo (3 minutes)

1. **View All Orders**
   ```bash
   curl http://localhost:8080/api/orders/admin/all \
     -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
   ```

2. **Process Refund**
   ```bash
   curl -X POST http://localhost:8080/api/orders/admin/1/refund \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN" \
     -d '{
       "amount": 500,
       "reason": "Demo refund"
     }'
   ```

3. **System Statistics**
   ```bash
   curl http://localhost:8080/api/orders/admin/stats \
     -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"
   ```

---

## 🔧 Troubleshooting

### Common Issues

**1. Docker Container Won't Start**
```bash
# Check logs
docker-compose logs vestige-backend

# Rebuild if needed
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

**2. Database Connection Issues**
```bash
# Check PostgreSQL container
docker-compose logs postgres

# Reset database
docker-compose down -v
docker-compose up -d
```

**3. Stripe API Errors**
- Verify test keys are correctly set in `.env`
- Check Stripe dashboard for API logs
- Ensure webhook endpoint is configured

**4. Frontend Connection Issues**
- Verify backend is accessible at `http://localhost:8080`
- Check CORS configuration
- Ensure JWT tokens are valid

### Health Checks

```bash
# Backend health
curl http://localhost:8080/actuator/health

# Database connection
curl http://localhost:8080/api/demo/status

# Stripe configuration
curl http://localhost:8080/api/demo/test-cards
```

---

## 📊 Demo Success Metrics

### What Should Work:
- ✅ Order creation and retrieval
- ✅ Payment intent generation
- ✅ Successful payment processing
- ✅ Payment decline handling
- ✅ 3D Secure authentication
- ✅ Order status updates
- ✅ Refund processing
- ✅ Admin statistics

### Logs to Monitor:
```
INFO  - Created PaymentIntent: pi_xxx for Order: 1
INFO  - Payment verification successful for Order: 1
INFO  - Order 1 successfully updated to status: PAID
INFO  - Webhook received: payment_intent.succeeded
```

---

## 🚦 Pre-Demo Checklist

### Environment Setup
- [ ] Docker and Docker Compose installed
- [ ] `.env` file configured with Stripe test keys
- [ ] Database credentials set
- [ ] JWT secrets configured

### Service Health
- [ ] Backend container running (`docker-compose ps`)
- [ ] Database accessible
- [ ] Stripe API responding
- [ ] Demo endpoints accessible

### Test Data
- [ ] Test cards documented and accessible
- [ ] Sample orders can be created
- [ ] Payment intents generate successfully
- [ ] Webhook endpoints configured (optional)

### Team Preparation
- [ ] Frontend team has backend URL
- [ ] Test card numbers shared
- [ ] Demo script reviewed
- [ ] Fallback plans prepared

---

## 🎯 Demo Tips

1. **Start Simple**: Begin with successful payment flow
2. **Show Error Handling**: Demonstrate declined payments
3. **Highlight Security**: Show JWT authentication
4. **Admin Features**: Display management capabilities
5. **Real-time Updates**: Show order status changes
6. **Log Monitoring**: Display backend logs during demo

---

## 📞 Support

**For Demo Day Support:**
- Backend logs: `docker-compose logs -f vestige-backend`
- Stripe Dashboard: https://dashboard.stripe.com/test/payments
- API Documentation: http://localhost:8080/swagger-ui/index.html

**Emergency Troubleshooting:**
```bash
# Quick restart
docker-compose restart vestige-backend

# Full reset
docker-compose down && docker-compose up -d
```

---

## 🔒 Security Notes

- **Test Mode Only**: All payments use Stripe test keys
- **No Real Money**: All transactions are simulated
- **JWT Required**: API endpoints require authentication
- **Admin Access**: Admin endpoints require ADMIN role
- **CORS Configured**: Frontend can connect safely

**Remember: This is a test environment - no real financial transactions occur!**
