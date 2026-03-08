-- ====================================
-- H2용 스키마 파일 (V1.0.1 + V1.0.2 통합)
-- ====================================

-- Grade 테이블
CREATE TABLE IF NOT EXISTS grade (
    grade_id INT NOT NULL AUTO_INCREMENT,
    grade VARCHAR(10) NULL,
    fee_rate DECIMAL(5,2) NULL,
    PRIMARY KEY (grade_id)
);

-- Users 테이블 (V1.0.1 + V1.0.2 통합)
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL,
    address VARCHAR(100) NULL,
    birth_date DATE NULL,
    profile_img_url VARCHAR(255) NULL,
    role VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NULL,
    phone_number VARCHAR(20) NULL,
    grade_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_provider_id UNIQUE (provider, provider_id),
    FOREIGN KEY (grade_id) REFERENCES grade (grade_id)
);

-- ====================================
-- 알림 관련 테이블
-- ====================================

CREATE TABLE IF NOT EXISTS notification_category (
    notification_category_id INT NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_category_id)
);

CREATE TABLE IF NOT EXISTS notification_template (
    notification_template_id BIGINT NOT NULL AUTO_INCREMENT,
    notification_category_id INT NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    title VARCHAR(50) NOT NULL,
    content VARCHAR(200) NOT NULL,
    redirect_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_template_id),
    FOREIGN KEY (notification_category_id) REFERENCES notification_category (notification_category_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS user_notification (
    user_notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_template_id BIGINT DEFAULT NULL,
    context_data JSON NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_notification_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    FOREIGN KEY (notification_template_id) REFERENCES notification_template (notification_template_id) ON DELETE SET NULL
);
