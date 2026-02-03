-- ====================================
-- V1.0.22: payments 테이블 토스페이먼츠 연동용 컬럼 추가
-- ====================================
-- 작성일: 2026-02-03
-- 설명: 토스페이먼츠 API 연동에 필요한 컬럼들 추가

-- 1. payment_key 컬럼 추가 (토스 paymentKey)
ALTER TABLE `payments`
    ADD COLUMN `payment_key` VARCHAR(200) NULL AFTER `order_id`;

-- 2. toss_order_id 컬럼 추가 (토스 orderId)
ALTER TABLE `payments`
    ADD COLUMN `toss_order_id` VARCHAR(64) NULL AFTER `payment_key`;

-- 3. requested_at 컬럼 추가 (결제 요청 시간)
ALTER TABLE `payments`
    ADD COLUMN `requested_at` DATETIME NULL AFTER `paid_at`;

-- 4. approved_at 컬럼 추가 (결제 승인 시간)
ALTER TABLE `payments`
    ADD COLUMN `approved_at` DATETIME NULL AFTER `requested_at`;

-- 5. receipt_url 컬럼 추가 (영수증 URL)
ALTER TABLE `payments`
    ADD COLUMN `receipt_url` VARCHAR(500) NULL AFTER `approved_at`;

-- 6. created_at 컬럼 추가 (엔티티 생성 시간)
ALTER TABLE `payments`
    ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `receipt_url`;

-- 7. pg_transaction_id를 nullable로 변경 (기존 컬럼 유지)
ALTER TABLE `payments`
    MODIFY COLUMN `pg_transaction_id` VARCHAR(255) NULL;

-- 8. paid_at 이미 nullable (V1.0.4에서 처리됨)
