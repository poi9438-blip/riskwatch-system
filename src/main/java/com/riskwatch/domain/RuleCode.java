package com.riskwatch.domain;

// 심사 규칙 (업무 명세 4장 표 순서 = 근거 목록 순서)
public enum RuleCode {
    PRODUCT_TARGET("상품 대상"),
    HOUSE_OWNERSHIP("주택 보유"),
    INCOME_LIMIT("부부합산 연소득"),
    HOUSE_PRICE_LIMIT("주택 가격"),
    LOAN_LIMIT("대출 금액"),
    DTI_LIMIT("DTI"),
    TERM_LIMIT("대출 기간");

    private final String label;

    RuleCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
