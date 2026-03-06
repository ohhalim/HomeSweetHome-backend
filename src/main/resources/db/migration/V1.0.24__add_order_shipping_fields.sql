ALTER TABLE orders
    ADD COLUMN recipient_name VARCHAR(100) NULL AFTER total_amount,
    ADD COLUMN recipient_phone VARCHAR(30) NULL AFTER recipient_name,
    ADD COLUMN shipping_address VARCHAR(500) NULL AFTER recipient_phone,
    ADD COLUMN shipping_request VARCHAR(500) NULL AFTER shipping_address;
