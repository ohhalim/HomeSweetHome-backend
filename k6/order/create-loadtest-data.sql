-- ====================================================================
-- Load Test Seed Data (Order/Community support)
-- =====================================================
-- Usage:
--   mysql -h <DB_HOST> -u <USER> -p <DB_NAME> < k6/order/create-loadtest-data.sql
-- or via docker:
--   docker exec -i homesweet-db mysql -u user -ppassword homesweet < k6/order/create-loadtest-data.sql
-- ====================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 1) 테스트용 구매자/판매자 유저 (USER_IDS 탐색용)
INSERT IGNORE INTO users (user_id, email, name, address, provider, provider_id, role, phone_number, birth_date)
VALUES
    (9001, 'k6-load-user01@local.test', 'K6 Load User 01', '서울시 강남구', 'GOOGLE', 'k6-load-user01', 'USER', '010-9001-0001', '1990-01-01'),
    (9002, 'k6-load-user02@local.test', 'K6 Load User 02', '서울시 강북구', 'GOOGLE', 'k6-load-user02', 'USER', '010-9002-0002', '1990-02-02'),
    (9003, 'k6-load-user03@local.test', 'K6 Load User 03', '서울시 강동구', 'GOOGLE', 'k6-load-user03', 'USER', '010-9003-0003', '1990-03-03'),
    (9004, 'k6-load-user04@local.test', 'K6 Load User 04', '서울시 중구',   'GOOGLE', 'k6-load-user04', 'USER', '010-9004-0004', '1990-04-04'),
    (9005, 'k6-load-user05@local.test', 'K6 Load User 05', '서울시 용산구', 'GOOGLE', 'k6-load-user05', 'USER', '010-9005-0005', '1990-05-05'),
    (9010, 'k6-load-seller@local.test', 'K6 Load Seller', '서울시 마포구', 'GOOGLE', 'k6-load-seller', 'SELLER', '010-9010-0000', '1988-10-10');

-- 2) 테스트 카테고리 (상품 조회/스크랩 시 필요)
INSERT IGNORE INTO product_category (category_id, name, parent_id, depth)
VALUES
    (9901, 'LOADTEST', NULL, 0),
    (9902, 'LOADTEST > 가전', 9901, 1),
    (9903, 'LOADTEST > 패션', 9901, 1);

-- 3) 테스트 상품(ON_SALE)
INSERT IGNORE INTO products (product_id, category_id, user_id, name, image_url, brand, base_price, discount_rate, description, shipping_price, status)
VALUES
    (8801, 9902, 9010, 'K6 부하테스트 상품 01', 'https://picsum.photos/640/480?1', 'LoadBrand', 12000, 0.00, 'Load test product 01', 0, 'ON_SALE'),
    (8802, 9902, 9010, 'K6 부하테스트 상품 02', 'https://picsum.photos/640/480?2', 'LoadBrand', 15000, 5.00, 'Load test product 02', 0, 'ON_SALE'),
    (8803, 9903, 9010, 'K6 부하테스트 상품 03', 'https://picsum.photos/640/480?3', 'LoadBrand', 10000, 10.00, 'Load test product 03', 3000, 'ON_SALE'),
    (8804, 9902, 9010, 'K6 부하테스트 상품 04', 'https://picsum.photos/640/480?4', 'LoadBrand', 18000, 0.00, 'Load test product 04', 0, 'ON_SALE'),
    (8805, 9903, 9010, 'K6 부하테스트 상품 05', 'https://picsum.photos/640/480?5', 'LoadBrand', 22000, 15.00, 'Load test product 05', 2000, 'ON_SALE');

-- 4) SKU (재고를 양수로 넣어야 주문 생성 가능)
INSERT IGNORE INTO sku (sku_id, product_id, price_adjustment, stock_quantity)
VALUES
    (99001, 8801, 0, 1000),
    (99002, 8801, 500, 800),
    (99003, 8802, 0, 1200),
    (99004, 8802, 1000, 700),
    (99005, 8803, 0, 500),
    (99006, 8804, 0, 1500),
    (99007, 8804, 300, 600),
    (99008, 8805, 0, 300),
    (99009, 8805, 200, 400),
    (99010, 8805, -100, 900);

-- 5) verification
SELECT 'users' AS table_name, COUNT(*) AS cnt FROM users WHERE user_id BETWEEN 9001 AND 9010;
SELECT 'categories' AS table_name, COUNT(*) AS cnt FROM product_category WHERE category_id BETWEEN 9901 AND 9903;
SELECT 'products' AS table_name, COUNT(*) AS cnt FROM products WHERE product_id BETWEEN 8801 AND 8805;
SELECT 'skus' AS table_name, COUNT(*) AS cnt FROM sku WHERE sku_id BETWEEN 99001 AND 99010;
SELECT 'profiles' AS table_name, COUNT(*) AS cnt FROM users WHERE user_id IN (9001,9002,9003,9004,9005,9010);
