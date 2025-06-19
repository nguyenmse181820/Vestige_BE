# 🛡️ Product Update API Security Enhancement

## Overview

The product update functionality has been enhanced with proper security controls to separate user and admin capabilities. This ensures that regular users can only update their own products with limited permissions, while admins have full control over all products.

## 🔐 Security Implementation

### 1. **User Product Update API**

**Endpoint:** `PATCH /api/products/{id}`
**Authorization:** `@PreAuthorize("hasRole('USER')")`
**Request Body:** `ProductUpdateRequest`

#### Security Controls:
- ✅ **Ownership Validation**: Users can only update products they own
- ✅ **Status Restrictions**: Users can only set `ACTIVE` or `INACTIVE` status
- ✅ **Sold Product Protection**: Cannot update products with `SOLD` status
- ✅ **Title Uniqueness**: Validates unique titles per seller

#### Example Request:
```json
{
  "title": "Updated Product Title",
  "description": "Updated description",
  "price": 150000,
  "originalPrice": 200000,
  "condition": "LIKE_NEW",
  "size": "M",
  "color": "Blue",
  "status": "ACTIVE",
  "categoryId": 1,
  "brandId": 2,
  "imageUrls": ["url1.jpg", "url2.jpg"]
}
```

#### Security Validations:
```java
// 1. Ownership check
if (!product.getSeller().getUserId().equals(sellerId)) {
    throw new UnauthorizedException("You are not authorized to update this product.");
}

// 2. Status validation
if (request.hasStatus() && !request.isValidUserStatus()) {
    throw new BusinessLogicException("Invalid status. Users can only set ACTIVE or INACTIVE status.");
}

// 3. Sold product protection
if (ProductStatus.SOLD.equals(product.getStatus())) {
    throw new BusinessLogicException("Cannot update a product that has been sold.");
}
```

### 2. **Admin Product Update API**

**Endpoint:** `PATCH /api/products/admin/{id}`
**Authorization:** `@PreAuthorize("hasRole('ADMIN')")`
**Request Body:** `AdminProductUpdateRequest`

#### Admin Capabilities:
- ✅ **Update Any Product**: No ownership restrictions
- ✅ **Full Status Control**: Can set any status including `SOLD`, `REPORTED`, `BANNED`
- ✅ **Ownership Transfer**: Can change product seller
- ✅ **Override Business Rules**: Can bypass normal restrictions
- ✅ **Admin Notes**: Can add administrative notes
- ✅ **Force Status Changes**: Can force status transitions

#### Example Request:
```json
{
  "title": "Admin Updated Title",
  "description": "Admin updated description",
  "price": 150000,
  "status": "REPORTED",
  "sellerId": 123,
  "adminNotes": "Product flagged for review",
  "forceSoldStatus": true
}
```

#### Admin-Specific Features:
```java
// 1. Ownership transfer
if (request.hasSellerId()) {
    User newSeller = userRepository.findById(request.getSellerId())
        .orElseThrow(() -> new ResourceNotFoundException("New seller not found"));
    product.setSeller(newSeller);
}

// 2. Force status changes
if (request.getStatus() == ProductStatus.SOLD && request.hasForceSoldStatus()) {
    product.setSoldAt(LocalDateTime.now());
}

// 3. Admin notes logging
if (request.hasAdminNotes()) {
    log.info("Admin {} added notes to product {}: {}", adminId, productId, request.getAdminNotes());
}
```

## 📊 API Comparison

| Feature | User API | Admin API |
|---------|----------|-----------|
| **Endpoint** | `PATCH /api/products/{id}` | `PATCH /api/products/admin/{id}` |
| **Ownership Check** | ✅ Required | ❌ Not Required |
| **Status Restrictions** | ✅ Only ACTIVE/INACTIVE | ❌ Any Status |
| **Sold Product Update** | ❌ Forbidden | ✅ Allowed |
| **Seller Transfer** | ❌ Not Allowed | ✅ Allowed |
| **Admin Notes** | ❌ Not Available | ✅ Available |
| **Force Operations** | ❌ Not Available | ✅ Available |

## 🔒 Request DTOs

### ProductUpdateRequest (User)
```java
public class ProductUpdateRequest {
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private ProductCondition condition;
    private String size;
    private String color;
    private ProductStatus status;          // Restricted to ACTIVE/INACTIVE
    private Long categoryId;
    private Long brandId;
    private List<String> imageUrls;
    
    // Validation method
    public boolean isValidUserStatus() {
        return status == null || 
               status == ProductStatus.ACTIVE || 
               status == ProductStatus.INACTIVE;
    }
}
```

### AdminProductUpdateRequest (Admin)
```java
public class AdminProductUpdateRequest {
    // All user fields plus:
    private String adminNotes;             // Admin-only field
    private Long sellerId;                 // Transfer ownership
    private Boolean forceSoldStatus;       // Force status override
    
    // Can set any ProductStatus including:
    // SOLD, REPORTED, BANNED, UNDER_REVIEW, etc.
}
```

## 🚀 Usage Examples

### 1. User Updates Their Product
```bash
# User updating their own product
curl -X PATCH "http://localhost:8080/api/products/123" \
  -H "Authorization: Bearer user_jwt_token" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "My Updated Product",
    "price": 100000,
    "status": "ACTIVE"
  }'
```

### 2. User Tries to Update Another User's Product
```bash
# This will return 401 Unauthorized
curl -X PATCH "http://localhost:8080/api/products/456" \
  -H "Authorization: Bearer user_jwt_token" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Trying to hack",
    "status": "SOLD"
  }'

# Response: 401 - "You are not authorized to update this product."
```

### 3. User Tries to Set Invalid Status
```bash
# This will return 400 Bad Request
curl -X PATCH "http://localhost:8080/api/products/123" \
  -H "Authorization: Bearer user_jwt_token" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SOLD"
  }'

# Response: 400 - "Invalid status. Users can only set ACTIVE or INACTIVE status."
```

### 4. Admin Updates Any Product
```bash
# Admin can update any product with any status
curl -X PATCH "http://localhost:8080/api/products/admin/456" \
  -H "Authorization: Bearer admin_jwt_token" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "REPORTED",
    "adminNotes": "Product flagged for inappropriate content",
    "sellerId": 789
  }'
```

### 5. Admin Forces Sold Status
```bash
# Admin can force status changes
curl -X PATCH "http://localhost:8080/api/products/admin/456" \
  -H "Authorization: Bearer admin_jwt_token" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SOLD",
    "forceSoldStatus": true,
    "adminNotes": "Manually marked as sold due to offline transaction"
  }'
```

## 🛡️ Security Benefits

### 1. **Principle of Least Privilege**
- Users only have permissions they need
- Admins have elevated permissions for management

### 2. **Data Integrity**
- Prevents unauthorized product modifications
- Maintains business rule consistency
- Protects sold product data

### 3. **Audit Trail**
- All admin actions are logged
- Clear separation of user vs admin operations
- Traceable ownership changes

### 4. **Role-Based Access Control**
- Spring Security integration
- Clear authorization boundaries
- Scalable permission model

## 🔧 Implementation Details

### Service Layer Security
```java
// User update method
@Transactional
public ProductDetailResponse updateProduct(Long productId, ProductUpdateRequest request, Long sellerId) {
    // 1. Find product
    // 2. Validate ownership
    // 3. Check business rules
    // 4. Validate status restrictions
    // 5. Apply updates
}

// Admin update method
@Transactional
public ProductDetailResponse updateProductAsAdmin(Long productId, AdminProductUpdateRequest request, Long adminId) {
    // 1. Find product (no ownership check)
    // 2. Log admin action
    // 3. Apply any updates (no restrictions)
    // 4. Handle special admin features
}
```

### Controller Layer Authorization
```java
// User endpoint - requires USER role + ownership validation
@PatchMapping("/{id}")
@PreAuthorize("hasRole('USER')")

// Admin endpoint - requires ADMIN role only
@PatchMapping("/admin/{id}")
@PreAuthorize("hasRole('ADMIN')")
```

## ✅ Testing Checklist

### User API Tests
- [ ] User can update their own product
- [ ] User cannot update other users' products
- [ ] User cannot set invalid statuses
- [ ] User cannot update sold products
- [ ] Validation errors are properly returned

### Admin API Tests
- [ ] Admin can update any product
- [ ] Admin can set any status
- [ ] Admin can transfer ownership
- [ ] Admin can force status changes
- [ ] Admin actions are properly logged

### Security Tests
- [ ] Unauthorized access is blocked
- [ ] JWT token validation works
- [ ] Role-based access is enforced
- [ ] Input validation prevents injection

## 🎯 Best Practices Applied

1. **Separation of Concerns**: Different endpoints for different roles
2. **Clear Authorization**: Role-based access with specific permissions
3. **Input Validation**: Proper DTO validation and business rule checks
4. **Audit Logging**: All admin actions are logged for traceability
5. **Fail-Safe Design**: Restrictive by default, permissive only when authorized
6. **Clean Architecture**: Service layer handles business logic, controller handles HTTP concerns
