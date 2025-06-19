# 🐳 Docker Demo Startup Script for Vestige Backend (PowerShell)

Write-Host "🎯 Starting Vestige Docker Demo Environment..." -ForegroundColor Green

# Check if .env file exists
if (-not (Test-Path .env)) {
    Write-Host "⚠️  .env file not found. Creating from template..." -ForegroundColor Yellow
    Copy-Item .env.docker .env
    Write-Host "📝 Please edit .env file with your Stripe test keys:" -ForegroundColor Cyan
    Write-Host "   STRIPE_SECRET_KEY=sk_test_..." -ForegroundColor White
    Write-Host "   STRIPE_PUBLISHABLE_KEY=pk_test_..." -ForegroundColor White
    Write-Host ""
    Write-Host "💡 Get your test keys from: https://dashboard.stripe.com/test/apikeys" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter after updating .env file"
}

# Load environment variables
if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}

# Validate Stripe keys
if (-not $env:STRIPE_SECRET_KEY -or -not $env:STRIPE_SECRET_KEY.StartsWith('sk_test_')) {
    Write-Host "❌ Invalid STRIPE_SECRET_KEY. Must start with 'sk_test_'" -ForegroundColor Red
    exit 1
}

if (-not $env:STRIPE_PUBLISHABLE_KEY -or -not $env:STRIPE_PUBLISHABLE_KEY.StartsWith('pk_test_')) {
    Write-Host "❌ Invalid STRIPE_PUBLISHABLE_KEY. Must start with 'pk_test_'" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Stripe test keys validated" -ForegroundColor Green

# Stop any existing containers
Write-Host "🧹 Cleaning up existing containers..." -ForegroundColor Yellow
docker-compose down

# Start the services
Write-Host "🚀 Starting services..." -ForegroundColor Green
docker-compose up -d

# Wait for services to be healthy
Write-Host "⏳ Waiting for services to start..." -ForegroundColor Yellow
Start-Sleep 10

# Check service status
Write-Host "🔍 Checking service status..." -ForegroundColor Cyan
docker-compose ps

# Verify backend health
Write-Host "🏥 Health check..." -ForegroundColor Cyan
$maxAttempts = 12
$attempt = 1

while ($attempt -le $maxAttempts) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Host "✅ Backend is healthy!" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "⏳ Attempt $attempt/$maxAttempts - Backend starting..." -ForegroundColor Yellow
        Start-Sleep 5
        $attempt++
    }
}

if ($attempt -gt $maxAttempts) {
    Write-Host "❌ Backend failed to start. Check logs:" -ForegroundColor Red
    Write-Host "   docker-compose logs vestige-backend" -ForegroundColor White
    exit 1
}

# Display demo information
Write-Host ""
Write-Host "🎉 DEMO ENVIRONMENT READY!" -ForegroundColor Green
Write-Host "==========================" -ForegroundColor Green
Write-Host ""
Write-Host "🌐 Backend URL: http://localhost:8080" -ForegroundColor Cyan
Write-Host "📚 Swagger UI: http://localhost:8080/swagger-ui/index.html" -ForegroundColor Cyan
Write-Host "🧪 Test Cards: http://localhost:8080/api/demo/test-cards" -ForegroundColor Cyan
Write-Host ""
Write-Host "🧪 TEST PAYMENT CARDS:" -ForegroundColor Yellow
Write-Host "  💳 Success: 4242424242424242" -ForegroundColor White
Write-Host "  ❌ Decline: 4000000000000002" -ForegroundColor White
Write-Host "  🔐 3D Secure: 4000002500003155" -ForegroundColor White
Write-Host "  📅 Expiry: 12/34 (any future date)" -ForegroundColor White
Write-Host "  🔒 CVC: 123 (any 3 digits)" -ForegroundColor White
Write-Host ""
Write-Host "📊 Monitoring:" -ForegroundColor Yellow
Write-Host "  docker-compose logs -f vestige-backend" -ForegroundColor White
Write-Host ""
Write-Host "🛑 To stop:" -ForegroundColor Yellow
Write-Host "  docker-compose down" -ForegroundColor White
Write-Host ""
Write-Host "🎬 Ready for demo! 🎯" -ForegroundColor Green
