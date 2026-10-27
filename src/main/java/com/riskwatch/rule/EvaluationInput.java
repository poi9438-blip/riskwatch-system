package com.riskwatch.rule;

import com.riskwatch.domain.HouseOwnership;
import com.riskwatch.domain.ProductCode;

import java.math.BigDecimal;

// 평가 입력 (업무 명세 3장, 형식 검증은 평가 API의 요청 DTO가 함)
public record EvaluationInput(
        ProductCode productCode,
        String applicantRef,
        String sourceRef,
        long requestedAmount,
        int termMonths,
        long housePrice,
        long householdAnnualIncome,
        BigDecimal dti,
        HouseOwnership houseOwnership,
        boolean firstHome,
        boolean newlywed,
        boolean newbornWithin2Years,
        boolean dualIncome,
        int childCount,
        String region) {
}
