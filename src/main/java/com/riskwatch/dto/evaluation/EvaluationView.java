package com.riskwatch.dto.evaluation;

import com.riskwatch.domain.ProductCode;
import com.riskwatch.domain.SourceSystem;
import com.riskwatch.domain.Verdict;
import com.riskwatch.rule.RuleReason;

import java.time.LocalDateTime;
import java.util.List;

// 평가 결과 (평가 응답과 조회 응답이 같은 모양). 입력값(연소득·DTI 등)은 근거의 실제값으로만 보여 줌
public record EvaluationView(
        String evaluationId,
        SourceSystem sourceSystem,
        String sourceRef,
        String applicantRef,
        ProductCode productCode,
        Verdict verdict,
        // HomeFin 호환 값
        String riskLevel,
        int riskScore,
        String recommendation,
        String criteriaVersion,
        List<RuleReason> reasons,
        // 입력이 없어 판정하지 않은 기준 (업무 명세 10장)
        List<String> notEvaluated,
        LocalDateTime createdAt) {
}
