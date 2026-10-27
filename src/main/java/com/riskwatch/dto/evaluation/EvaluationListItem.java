package com.riskwatch.dto.evaluation;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.domain.Verdict;

import java.time.LocalDateTime;

// 운영자 평가 기록 목록 한 줄 (연소득·DTI 없음)
public record EvaluationListItem(String evaluationId, SourceSystem sourceSystem, String sourceRef, String applicantRef,
        ProductCode productCode, Verdict verdict, String riskLevel, int riskScore, String criteriaVersion,
        LocalDateTime createdAt) {
}
