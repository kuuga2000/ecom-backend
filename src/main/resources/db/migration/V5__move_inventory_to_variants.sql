ALTER TABLE product_variants
    ADD COLUMN inventory_quantity INTEGER NOT NULL DEFAULT 0
    CONSTRAINT ck_variant_inventory_nonnegative CHECK (inventory_quantity >= 0);

UPDATE product_variants v
SET inventory_quantity = p.inventory_quantity
FROM products p
WHERE p.default_variant_id = v.id;

ALTER TABLE products DROP COLUMN inventory_quantity;
