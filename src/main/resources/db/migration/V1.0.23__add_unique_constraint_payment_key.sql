-- ====================================
-- V1.0.23: payments 테이블 UNIQUE 제약 강화
-- ====================================
-- 작성일: 2026-02-13
-- 설명: 동시성 상황에서 중복 결제 삽입을 DB 레벨에서 방지
--       payment_key UNIQUE + order_id UNIQUE 추가
--       기존 중복 데이터가 있을 경우 안전하게 정리 후 제약 추가

-- 1. 기존 NULL payment_key 레코드에 임시값 부여
UPDATE `payments` SET `payment_key` = CONCAT('LEGACY_', `payment_id`) WHERE `payment_key` IS NULL;

-- 2. 중복 payment_key 정리 (가장 최신 레코드만 보존, 나머지 삭제)
DELETE p1 FROM `payments` p1
INNER JOIN `payments` p2
ON p1.`payment_key` = p2.`payment_key`
AND p1.`payment_id` < p2.`payment_id`;

-- 3. 중복 order_id 정리 (가장 최신 레코드만 보존, 나머지 삭제)
DELETE p1 FROM `payments` p1
INNER JOIN `payments` p2
ON p1.`order_id` = p2.`order_id`
AND p1.`payment_id` < p2.`payment_id`;

-- 4. payment_key NOT NULL + UNIQUE 제약 추가
ALTER TABLE `payments`
    MODIFY COLUMN `payment_key` VARCHAR(200) NOT NULL,
    ADD CONSTRAINT `uk_payments_payment_key` UNIQUE (`payment_key`);

-- 5. order_id UNIQUE 제약 추가 (한 주문 - 한 결제 정책 강제)
ALTER TABLE `payments`
    ADD CONSTRAINT `uk_payments_order_id` UNIQUE (`order_id`);
