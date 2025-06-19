-- Fix all status constraints to match enum values
-- Run this SQL script to allow all enum status values in the database

-- Step 1: Check current constraints
SELECT conname, pg_get_constraintdef(oid) AS constraint_definition 
FROM pg_constraint 
WHERE conrelid = 'orders'::regclass 
  AND contype = 'c' 
  AND conname LIKE '%status%';

SELECT conname, pg_get_constraintdef(oid) AS constraint_definition 
FROM pg_constraint 
WHERE conrelid = 'order_items'::regclass 
  AND contype = 'c' 
  AND (conname LIKE '%escrow_status%' OR conname LIKE '%status%');

SELECT conname, pg_get_constraintdef(oid) AS constraint_definition 
FROM pg_constraint 
WHERE conrelid = 'transactions'::regclass 
  AND contype = 'c' 
  AND (conname LIKE '%escrow_status%' OR conname LIKE '%status%');

-- Step 2: Drop the existing constraints
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS order_items_escrow_status_check;
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS order_items_status_check;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_escrow_status_check;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;

-- Step 3: Create new constraints with all valid enum values
-- Fix orders table constraint
ALTER TABLE orders ADD CONSTRAINT orders_status_check 
CHECK (status IN ('PENDING', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED', 'EXPIRED'));

-- Fix order_items table escrow_status constraint
ALTER TABLE order_items ADD CONSTRAINT order_items_escrow_status_check 
CHECK (escrow_status IN ('HOLDING', 'RELEASED', 'TRANSFERRED', 'REFUNDED', 'CANCELLED', 'TRANSFER_FAILED'));

-- Fix order_items table status constraint
ALTER TABLE order_items ADD CONSTRAINT order_items_status_check 
CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'));

-- Fix transactions table escrow_status constraint
ALTER TABLE transactions ADD CONSTRAINT transactions_escrow_status_check 
CHECK (escrow_status IN ('HOLDING', 'RELEASED', 'TRANSFERRED', 'REFUNDED', 'CANCELLED', 'TRANSFER_FAILED'));

-- Fix transactions table status constraint (TransactionStatus enum)
ALTER TABLE transactions ADD CONSTRAINT transactions_status_check 
CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'));

-- Step 4: Verify the new constraints
SELECT conname, pg_get_constraintdef(oid) AS constraint_definition 
FROM pg_constraint 
WHERE conrelid = 'orders'::regclass 
  AND contype = 'c' 
  AND conname LIKE '%status%';

SELECT conname, pg_get_constraintdef(oid) AS constraint_definition 
FROM pg_constraint 
WHERE conrelid = 'order_items'::regclass 
  AND contype = 'c' 
  AND (conname LIKE '%escrow_status%' OR conname LIKE '%status%');

SELECT conname, pg_get_constraintdef(oid) AS constraint_definition 
FROM pg_constraint 
WHERE conrelid = 'transactions'::regclass 
  AND contype = 'c' 
  AND (conname LIKE '%escrow_status%' OR conname LIKE '%status%');
