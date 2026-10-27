-- 운영자 계정과 로그인 실패 (FinFlow와 같은 구조, 업무 명세 2장)

-- 운영자 계정 (비밀번호는 BCrypt 해시)
CREATE TABLE operators (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_operators_email UNIQUE (email)
);

-- 운영자 로그인 실패 (시도 제한용, 이메일은 HMAC 값만)
CREATE TABLE operator_login_failures (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email_hash  VARCHAR(80)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_login_failures_email_created ON operator_login_failures (email_hash, created_at);
