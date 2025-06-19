# 🎯 Demo Test Data Setup Script (PowerShell)

Write-Host "🚀 Setting up Vestige Demo Environment..." -ForegroundColor Green

# Check if required environment variables are set
if (-not $env:STRIPE_SECRET_KEY) {
    Write-Host "❌ STRIPE_SECRET_KEY not set. Please set your test secret key:" -ForegroundColor Red
    Write-Host '$env:STRIPE_SECRET_KEY = "sk_test_..."' -ForegroundColor Yellow
    exit 1
}

if (-not $env:STRIPE_PUBLISHABLE_KEY) {
    Write-Host "❌ STRIPE_PUBLISHABLE_KEY not set. Please set your test publishable key:" -ForegroundColor Red
    Write-Host '$env:STRIPE_PUBLISHABLE_KEY = "pk_test_..."' -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Stripe test keys configured" -ForegroundColor Green

# Set default database values if not provided
if (-not $env:DATABASE_URL) { $env:DATABASE_URL = "jdbc:postgresql://localhost:5432/vestige_db" }
if (-not $env:DATABASE_USERNAME) { $env:DATABASE_USERNAME = "postgres" }
if (-not $env:DATABASE_PASSWORD) { $env:DATABASE_PASSWORD = "12345" }

Write-Host "✅ Database configuration set" -ForegroundColor Green

# Display demo card information
Write-Host ""
Write-Host "🧪 TEST PAYMENT CARDS FOR DEMO:" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host "💳 Successful Payment:" -ForegroundColor Green
Write-Host "   Card: 4242424242424242"
Write-Host "   Expiry: 12/34 (any future date)"
Write-Host "   CVC: 123 (any 3 digits)"
Write-Host "   Postal: 12345 (any valid code)"
Write-Host ""
Write-Host "❌ Declined Payment:" -ForegroundColor Red
Write-Host "   Card: 4000000000000002"
Write-Host "   (Use same expiry/CVC as above)"
Write-Host ""

# Start the application
Write-Host "🚀 Starting Vestige Backend..." -ForegroundColor Green
Write-Host "Backend will be available at: http://localhost:8080"
Write-Host "Swagger UI: http://localhost:8080/swagger-ui/index.html"
Write-Host ""
Write-Host "📊 Payment Flow Monitoring:" -ForegroundColor Cyan
Write-Host "Watch for these log patterns:"
Write-Host "  - 'Created PaymentIntent' - Payment initialization"
Write-Host "  - 'Payment verification successful' - Payment confirmation"  
Write-Host "  - 'Order X successfully updated' - Order completion"
Write-Host ""

# Run the Spring Boot application
mvn spring-boot:run
