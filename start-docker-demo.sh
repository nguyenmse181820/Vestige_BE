#!/bin/bash

# 🐳 Docker Demo Startup Script for Vestige Backend

echo "🎯 Starting Vestige Docker Demo Environment..."

# Check if .env file exists
if [ ! -f .env ]; then
    echo "⚠️  .env file not found. Creating from template..."
    cp .env.docker .env
    echo "📝 Please edit .env file with your Stripe test keys:"
    echo "   STRIPE_SECRET_KEY=sk_test_..."
    echo "   STRIPE_PUBLISHABLE_KEY=pk_test_..."
    echo ""
    echo "💡 Get your test keys from: https://dashboard.stripe.com/test/apikeys"
    echo ""
    read -p "Press Enter after updating .env file..."
fi

# Validate Stripe keys
source .env
if [[ ! $STRIPE_SECRET_KEY =~ ^sk_test_ ]]; then
    echo "❌ Invalid STRIPE_SECRET_KEY. Must start with 'sk_test_'"
    exit 1
fi

if [[ ! $STRIPE_PUBLISHABLE_KEY =~ ^pk_test_ ]]; then
    echo "❌ Invalid STRIPE_PUBLISHABLE_KEY. Must start with 'pk_test_'"
    exit 1
fi

echo "✅ Stripe test keys validated"

# Stop any existing containers
echo "🧹 Cleaning up existing containers..."
docker-compose down

# Start the services
echo "🚀 Starting services..."
docker-compose up -d

# Wait for services to be healthy
echo "⏳ Waiting for services to start..."
sleep 10

# Check service status
echo "🔍 Checking service status..."
docker-compose ps

# Verify backend health
echo "🏥 Health check..."
max_attempts=12
attempt=1

while [ $attempt -le $max_attempts ]; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ Backend is healthy!"
        break
    else
        echo "⏳ Attempt $attempt/$max_attempts - Backend starting..."
        sleep 5
        ((attempt++))
    fi
done

if [ $attempt -gt $max_attempts ]; then
    echo "❌ Backend failed to start. Check logs:"
    echo "   docker-compose logs vestige-backend"
    exit 1
fi

# Display demo information
echo ""
echo "🎉 DEMO ENVIRONMENT READY!"
echo "=========================="
echo ""
echo "🌐 Backend URL: http://localhost:8080"
echo "📚 Swagger UI: http://localhost:8080/swagger-ui/index.html"
echo "🧪 Test Cards: http://localhost:8080/api/demo/test-cards"
echo ""
echo "🧪 TEST PAYMENT CARDS:"
echo "  💳 Success: 4242424242424242"
echo "  ❌ Decline: 4000000000000002"
echo "  🔐 3D Secure: 4000002500003155"
echo "  📅 Expiry: 12/34 (any future date)"
echo "  🔒 CVC: 123 (any 3 digits)"
echo ""
echo "📊 Monitoring:"
echo "  docker-compose logs -f vestige-backend"
echo ""
echo "🛑 To stop:"
echo "  docker-compose down"
echo ""
echo "🎬 Ready for demo! 🎯"
