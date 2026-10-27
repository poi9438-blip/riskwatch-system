package com.riskwatch.rule;

import com.riskwatch.domain.RuleCode;
import com.riskwatch.domain.RuleResult;

import java.math.BigDecimal;

// 규칙 하나의 근거 (기준값·실제값은 금액이면 원, 비율이면 0.60처럼, 해당 없으면 null)
public record RuleReason(RuleCode code, String name, BigDecimal threshold, BigDecimal actual, RuleResult result,
        String detail) {
}
