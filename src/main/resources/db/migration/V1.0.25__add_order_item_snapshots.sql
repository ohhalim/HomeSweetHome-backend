ALTER TABLE order_items
    ADD COLUMN source_cart_id BIGINT NULL AFTER sku_id,
    ADD COLUMN product_name VARCHAR(100) NULL AFTER source_cart_id;

UPDATE order_items oi
    JOIN sku s ON oi.sku_id = s.sku_id
    JOIN products p ON s.product_id = p.product_id
SET oi.product_name = p.name
WHERE oi.product_name IS NULL;

ALTER TABLE order_items
    MODIFY COLUMN product_name VARCHAR(100) NOT NULL;

CREATE INDEX idx_order_items_source_cart_id ON order_items (source_cart_id);
