-- 심사 기준표(버전)와 평가 기록 (업무 명세 5장·6장·9장)

-- 기준 버전: 만든 뒤 바뀌지 않음, 바꾸려면 새 버전
CREATE TABLE criteria_versions (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    -- 버전 이름 (예: 2026-10-26)
    version         VARCHAR(30)   NOT NULL,
    -- 이 시각부터 평가에 쓰임 (UTC)
    effective_from  DATETIME(6)   NOT NULL,
    -- 만든 사람 (운영자 id 또는 system)
    created_by      VARCHAR(64)   NOT NULL,
    -- 만든 사유 (필수)
    reason          VARCHAR(500)  NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_criteria_versions_version UNIQUE (version)
);

CREATE INDEX idx_criteria_versions_effective ON criteria_versions (effective_from);

-- 기준값: 버전 × 상품 × 기준 키 (금액은 원, 비율은 0.60처럼)
CREATE TABLE criteria_values (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    version_id    BIGINT         NOT NULL,
    product_code  VARCHAR(30)    NOT NULL,
    criteria_key  VARCHAR(60)    NOT NULL,
    -- 값은 키워드(value)를 피해 criteria_value
    criteria_value DECIMAL(15, 4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_criteria_values_version FOREIGN KEY (version_id) REFERENCES criteria_versions (id),
    CONSTRAINT uk_criteria_values_key UNIQUE (version_id, product_code, criteria_key)
);

-- 평가 기록: 입력·판정·근거·기준 버전 (나중에 같은 판정을 다시 설명할 수 있게)
CREATE TABLE risk_evaluations (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    -- 외부에 보이는 평가 번호 (UUID)
    evaluation_id            VARCHAR(36)   NOT NULL,
    -- 요청한 서버 (HOMEFIN / FINFLOW, 서비스 토큰으로 정함)
    source_system            VARCHAR(20)   NOT NULL,
    source_ref               VARCHAR(64),
    applicant_ref            VARCHAR(64)   NOT NULL,
    product_code             VARCHAR(30)   NOT NULL,
    verdict                  VARCHAR(20)   NOT NULL,
    risk_level               VARCHAR(10)   NOT NULL,
    risk_score               INT           NOT NULL,
    recommendation           VARCHAR(500)  NOT NULL,
    criteria_version_id      BIGINT        NOT NULL,
    -- 규칙별 근거 (JSON 배열)
    reasons                  VARCHAR(4000) NOT NULL,
    -- 입력값 (연소득·DTI는 AES-256-GCM 암호문)
    requested_amount         BIGINT        NOT NULL,
    term_months              INT           NOT NULL,
    house_price              BIGINT        NOT NULL,
    household_annual_income  VARCHAR(255)  NOT NULL,
    dti                      VARCHAR(255)  NOT NULL,
    house_ownership          VARCHAR(10)   NOT NULL,
    first_home               BOOLEAN       NOT NULL,
    newlywed                 BOOLEAN       NOT NULL,
    newborn_within_2_years   BOOLEAN       NOT NULL,
    dual_income              BOOLEAN       NOT NULL,
    child_count              INT           NOT NULL,
    region                   VARCHAR(20),
    trace_id                 VARCHAR(64),
    created_at               DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_risk_evaluations_evaluation_id UNIQUE (evaluation_id),
    CONSTRAINT fk_risk_evaluations_criteria_version FOREIGN KEY (criteria_version_id) REFERENCES criteria_versions (id)
);

-- 운영자 평가 기록 목록 (상품·판정별 최신순)
CREATE INDEX idx_risk_evaluations_product_verdict ON risk_evaluations (product_code, verdict, created_at);
-- 서버별 평가 조회
CREATE INDEX idx_risk_evaluations_source ON risk_evaluations (source_system, source_ref);
