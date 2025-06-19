# 👥 Team Demo Handoff Instructions

## 🎯 Quick Start for Frontend Team

### For Docker Demo (Recommended)

1. **Clone/Pull Latest Code**
   ```bash
   git pull origin main
   ```

2. **Setup Environment**
   ```bash
   # Copy template
   cp .env.docker .env
   
   # Edit .env file with Stripe test keys
   # STRIPE_SECRET_KEY=sk_test_YOUR_KEY
   # STRIPE_PUBLISHABLE_KEY=pk_test_YOUR_KEY
   ```

3. **Start Demo Environment**
   ```powershell
   # Windows
   .\start-docker-demo.ps1
   
   # Linux/Mac
   ./start-docker-demo.sh
   ```

4. **Verify Setup**
   - Backend: http://localhost:8080
   - Health: http://localhost:8080/actuator/health
   - Test Cards: http://localhost:8080/api/demo/test-cards

### Frontend Integration

**Backend URL**: `http://localhost:8080`

**Test Cards for Demo**:
```javascript
const testCards = {
  success: '4242424242424242',    // Always succeeds
  decline: '4000000000000002',    // Always declines
  auth3d: '4000002500003155'      // Requires 3D Secure
};
```

**Key Endpoints**:
- `POST /api/orders` - Create order
- `POST /api/stripe/create-payment-intent` - Start payment
- `POST /api/stripe/confirm-payment` - Complete payment
- `GET /api/orders/{id}` - Check order status

---

## 📋 Demo Checklist

### Before Demo
- [ ] Backend running via Docker
- [ ] Test cards accessible
- [ ] Frontend connected to backend
- [ ] Test payment flow works
- [ ] Error scenarios prepared

### During Demo
- [ ] Show successful payment with `4242424242424242`
- [ ] Show payment decline with `4000000000000002`  
- [ ] Show 3D Secure with `4000002500003155`
- [ ] Show order status updates
- [ ] Show admin endpoints (optional)

### Demo Flow
1. **Create Order** → Show order in PENDING status
2. **Payment Intent** → Show payment initialization
3. **Process Payment** → Use test card `4242424242424242`
4. **Payment Success** → Show order updates to PAID
5. **Order Complete** → Show final order details

---

## 🆘 Emergency Troubleshooting

**Backend Won't Start**:
```bash
docker-compose logs vestige-backend
docker-compose restart vestige-backend
```

**Database Issues**:
```bash
docker-compose down -v
docker-compose up -d
```

**Quick Reset**:
```bash
docker-compose down && docker-compose up -d
```

---

## 📞 Contact for Demo Support

- Check logs: `docker-compose logs -f vestige-backend`
- Restart services: `docker-compose restart`
- Full documentation: See `DEMO_PAYMENT_GUIDE.md`

**Ready for successful demo! 🎉**
