package com.riskwatch.domain;

// 규칙 하나의 결과
public enum RuleResult {
    // 충족
    PASS,
    // 확인 필요 (판정: 검토 필요)
    WARN,
    // 미충족 (판정: 부적격)
    FAIL
}
