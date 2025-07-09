-- Migration to add slug column to products table
-- Run this SQL script to add slug support to existing products

-- Add slug column
ALTER TABLE products ADD COLUMN slug VARCHAR(150);

-- Add unique index on slug (after data migration)
-- We'll add this after updating existing products with slugs

-- Temporary function to generate slug from title
CREATE OR REPLACE FUNCTION generate_slug(input_text TEXT) 
RETURNS TEXT AS $$
DECLARE
    slug_text TEXT;
BEGIN
    -- Convert to lowercase and replace spaces with hyphens
    slug_text := LOWER(TRIM(input_text));
    slug_text := REGEXP_REPLACE(slug_text, '\s+', '-', 'g');
    
    -- Remove non-alphanumeric characters except hyphens
    slug_text := REGEXP_REPLACE(slug_text, '[^a-z0-9\-]', '', 'g');
    
    -- Replace multiple consecutive hyphens with single hyphen
    slug_text := REGEXP_REPLACE(slug_text, '-+', '-', 'g');
    
    -- Remove leading and trailing hyphens
    slug_text := REGEXP_REPLACE(slug_text, '^-+|-+$', '', 'g');
    
    -- Limit length to 100 characters
    IF LENGTH(slug_text) > 100 THEN
        slug_text := SUBSTRING(slug_text FROM 1 FOR 100);
        -- Make sure we don't cut in the middle of a word
        IF POSITION('-' IN REVERSE(SUBSTRING(slug_text FROM 51))) > 0 THEN
            slug_text := SUBSTRING(slug_text FROM 1 FOR 100 - POSITION('-' IN REVERSE(SUBSTRING(slug_text FROM 51))));
        END IF;
    END IF;
    
    -- If empty, return 'product'
    IF slug_text = '' OR slug_text IS NULL THEN
        slug_text := 'product';
    END IF;
    
    RETURN slug_text;
END;
$$ LANGUAGE plpgsql;

-- Update existing products with generated slugs
-- This creates professional slugs and handles edge cases properly:
-- 1. First user gets clean slug
-- 2. Different sellers get -by-username  
-- 3. Same seller multiple listings get -2, -3, etc.
-- 4. If first user lists again after others, they get -by-username-2

UPDATE products 
SET slug = (
    WITH base_slugs AS (
        SELECT 
            p.product_id,
            CASE 
                -- Full context: brand + title + color + size + condition
                WHEN b.name IS NOT NULL AND p.color IS NOT NULL AND p.size IS NOT NULL AND p.condition != 'NEW' THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.color) || '-' || generate_slug(p.size) || '-' || generate_slug(p.condition::text)
                
                -- Brand + title + color + size (no condition if NEW)
                WHEN b.name IS NOT NULL AND p.color IS NOT NULL AND p.size IS NOT NULL THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.color) || '-' || generate_slug(p.size)
                
                -- Brand + title + color + condition
                WHEN b.name IS NOT NULL AND p.color IS NOT NULL AND p.condition != 'NEW' THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.color) || '-' || generate_slug(p.condition::text)
                
                -- Brand + title + color
                WHEN b.name IS NOT NULL AND p.color IS NOT NULL THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.color)
                
                -- Brand + title + size + condition
                WHEN b.name IS NOT NULL AND p.size IS NOT NULL AND p.condition != 'NEW' THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.size) || '-' || generate_slug(p.condition::text)
                
                -- Brand + title + size
                WHEN b.name IS NOT NULL AND p.size IS NOT NULL THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.size)
                
                -- Brand + title + condition
                WHEN b.name IS NOT NULL AND p.condition != 'NEW' THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title) || '-' || generate_slug(p.condition::text)
                
                -- Brand + title
                WHEN b.name IS NOT NULL THEN 
                    generate_slug(b.name) || '-' || generate_slug(p.title)
                
                -- Fallback: just title
                ELSE 
                    generate_slug(p.title)
            END as base_slug,
            p.seller_id,
            u.username
        FROM products p
        LEFT JOIN brands b ON p.brand_id = b.brand_id
        LEFT JOIN users u ON p.seller_id = u.user_id
    ),    seller_counts AS (
        SELECT 
            base_slug,
            COUNT(DISTINCT seller_id) as seller_count,
            MIN(product_id) as first_product_id
        FROM base_slugs
        GROUP BY base_slug
    ),
    first_sellers AS (
        SELECT 
            bs.base_slug,
            bs.seller_id as first_seller_id
        FROM base_slugs bs
        INNER JOIN seller_counts sc ON bs.base_slug = sc.base_slug AND bs.product_id = sc.first_product_id
    ),
    slug_analysis AS (
        SELECT 
            bs.product_id,
            bs.base_slug,
            bs.seller_id,
            bs.username,
            sc.seller_count,
            fs.first_seller_id,
            -- Number the products within same seller and base slug
            ROW_NUMBER() OVER (PARTITION BY bs.base_slug, bs.seller_id ORDER BY bs.product_id) as seller_listing_num
        FROM base_slugs bs
        INNER JOIN seller_counts sc ON bs.base_slug = sc.base_slug
        INNER JOIN first_sellers fs ON bs.base_slug = fs.base_slug
    )
    SELECT 
        CASE 
            -- Case 1: Only one seller has this product
            WHEN sa.seller_count = 1 THEN
                CASE 
                    WHEN sa.seller_listing_num = 1 THEN sa.base_slug
                    ELSE sa.base_slug || '-' || sa.seller_listing_num
                END
            
            -- Case 2: Multiple sellers have this product
            ELSE
                CASE 
                    -- First seller, first listing: gets clean slug
                    WHEN sa.seller_id = sa.first_seller_id AND sa.seller_listing_num = 1 THEN 
                        sa.base_slug
                    
                    -- First seller, additional listings: gets -2, -3, etc.
                    WHEN sa.seller_id = sa.first_seller_id AND sa.seller_listing_num > 1 THEN 
                        sa.base_slug || '-' || sa.seller_listing_num
                    
                    -- Other sellers, first listing: gets -by-username
                    WHEN sa.seller_id != sa.first_seller_id AND sa.seller_listing_num = 1 THEN 
                        sa.base_slug || '-by-' || generate_slug(sa.username)
                    
                    -- Other sellers, additional listings: gets -by-username-2, -by-username-3, etc.
                    ELSE 
                        sa.base_slug || '-by-' || generate_slug(sa.username) || '-' || sa.seller_listing_num
                END
        END
    FROM slug_analysis sa
    WHERE sa.product_id = products.product_id
)
WHERE slug IS NULL;

-- Make slug NOT NULL
ALTER TABLE products ALTER COLUMN slug SET NOT NULL;

-- Add unique constraint
ALTER TABLE products ADD CONSTRAINT products_slug_unique UNIQUE (slug);

-- Drop the temporary function
DROP FUNCTION generate_slug(TEXT);

-- Create index for better performance on slug lookups
CREATE INDEX idx_products_slug ON products (slug);

COMMENT ON COLUMN products.slug IS 'URL-friendly version of the product title, used for SEO-friendly URLs';
