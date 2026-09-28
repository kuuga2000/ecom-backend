ALTER TABLE products ADD COLUMN sku VARCHAR(100);

UPDATE products
SET sku = CASE name
    WHEN 'Mechanical Keyboard' THEN 'KEY-MECH-001'
    WHEN 'Wireless Mouse' THEN 'MOU-WLS-001'
    WHEN 'USB-C Hub' THEN 'HUB-USBC-001'
    ELSE 'PRODUCT-' || id
END;

ALTER TABLE products ALTER COLUMN sku SET NOT NULL;

CREATE UNIQUE INDEX uq_products_sku ON products (sku);
