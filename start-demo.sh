#!/bin/bash

# 🎯 Demo Test Data Setup Script

echo "🚀 Setting up Vestige Demo Environment..."

# Check if required environment variables are set
if [ -z "$STRIPE_SECRET_KEY" ]; then
    echo "❌ STRIPE_SECRET_KEY not set. Please set your test secret key:"
    echo "export STRIPE_SECRET_KEY='sk_test_...'"
    exit 1
fi

if [ -z "$STRIPE_PUBLISHABLE_KEY" ]; then
    echo "❌ STRIPE_PUBLISHABLE_KEY not set. Please set your test publishable key:"
    echo "export STRIPE_PUBLISHABLE_KEY='pk_test_...'"
    exit 1
fi

echo "✅ Stripe test keys configured"

# Set default database values if not provided
export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/vestige_db}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-postgres}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-12345}"

echo "✅ Database configuration set"

# Display demo card information
echo ""
echo "🧪 TEST PAYMENT CARDS FOR DEMO:"
echo "================================"
echo "💳 Successful Payment:"
echo "   Card: 4242424242424242"
echo "   Expiry: 12/34 (any future date)"
echo "   CVC: 123 (any 3 digits)"
echo "   Postal: 12345 (any valid code)"
echo ""
echo "❌ Declined Payment:"
echo "   Card: 4000000000000002"
echo "   (Use same expiry/CVC as above)"
echo ""

# Start the application
echo "🚀 Starting Vestige Backend..."
echo "Backend will be available at: http://localhost:8080"
echo "Swagger UI: http://localhost:8080/swagger-ui/index.html"
echo ""
echo "📊 Payment Flow Monitoring:"
echo "Watch for these log patterns:"
echo "  - 'Created PaymentIntent' - Payment initialization"
echo "  - 'Payment verification successful' - Payment confirmation"
echo "  - 'Order X successfully updated' - Order completion"
echo ""

# Run the Spring Boot application
mvn spring-boot:run
